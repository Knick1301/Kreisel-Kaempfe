package org.example.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.dtos.ArenaDto;
import org.example.dtos.ArenaResultDto;
import org.example.dtos.ArenaRoomDto;
import org.example.dtos.LaunchChallengeDto;
import org.example.entities.Beyblade;
import org.example.entities.FighterSnapshot;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.Archetype;
import org.example.game.BattleMode;
import org.example.game.BotLauncher;
import org.example.game.Launch;
import org.example.game.LaunchChallenge;
import org.example.game.LaunchInput;
import org.example.game.LaunchRules;
import org.example.game.Side;
import org.example.game.StyleVector;
import org.example.game.arena.ArenaBotAi;
import org.example.game.arena.ArenaGeometry;
import org.example.game.arena.ArenaPhysics;
import org.example.game.arena.ArenaPhysics.Event;
import org.example.game.arena.ArenaPhysics.EventType;
import org.example.game.arena.ArenaPhysics.FighterSetup;
import org.example.game.arena.ArenaPhysics.Input;
import org.example.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Arena-Räume: Die Physik läuft hier auf dem Server, mit {@link ArenaPhysics#SIM_HZ} Schritten pro Sekunde.
 * Die Browser schicken per WebSocket nur ihre Eingaben und bekommen ~30× pro Sekunde den Stand. Dadurch ist
 * der Arena-Rang so fälschungssicher wie der klassische.
 *
 * <p>Ablauf: LAUNCH (beide spinnen an, die KI sofort) → COUNTDOWN (3 s) → FIGHT → DONE. Eine Ult friert den
 * Kampf für beide kurz ein (Einblendung). Wer im Kampf {@link #DISCONNECT_MS} ohne Verbindung ist, verliert.
 */
@Service
public class ArenaRoomService {

    private static final Logger log = LoggerFactory.getLogger(ArenaRoomService.class);

    public enum Mode { BOT, PVP }

    public enum Status { LAUNCH, COUNTDOWN, FIGHT, DONE }

    static final int COUNTDOWN_MS = 3000;
    /** Ult-Einblendung: so lange steht der Kampf für beide */
    static final int ULT_FREEZE_MS = 900;
    static final int DISCONNECT_MS = 5000;
    /** Stand an die Browser, alle … ms */
    static final int BROADCAST_MS = 33;
    static final int TICK_MS = 8;
    /** Fertige oder liegen gebliebene Räume werden danach entfernt */
    static final long RETENTION_MS = 10 * 60_000;
    /** Anspinn-Genauigkeit normaler KI-Gegner */
    static final double BOT_SKILL = LaunchRules.BOT_SKILL;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final BeybladeService beyblades;
    private final BattleService battles;
    private final ArenaService ranking;
    private final ObjectMapper json;
    private final FriendService friends;
    private final boolean autoTick;
    private final Random random = new Random();
    private ScheduledExecutorService ticker;

    public ArenaRoomService(UserRepository users, BeybladeService beyblades, BattleService battles,
                            ArenaService ranking, ObjectMapper json, FriendService friends,
                            @Value("${arena.tick:true}") boolean autoTick) {
        this.users = users;
        this.beyblades = beyblades;
        this.battles = battles;
        this.ranking = ranking;
        this.json = json;
        this.friends = friends;
        this.autoTick = autoTick;
    }

    @PostConstruct
    void startTicker() {
        friends.setArenaStarter(this::createPvp);
        if (!autoTick) return;
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arena-ticker");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleAtFixedRate(this::tickAll, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopTicker() {
        if (ticker != null) ticker.shutdownNow();
    }

    // ---------------------------------------------------------------- Räume anlegen

    /** Kampf gegen einen zufälligen KI-Gegner in einer zufälligen Arena (gewertet bis Gold). */
    public ArenaRoomDto createBot(String username, Long beybladeId) {
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);
        Room room = newRoom(Mode.BOT, false, me.getUsername(), FighterSnapshot.of(me, mine),
                battles.botUser().getUsername(), battles.drawBot(mine.getStats()), BOT_SKILL,
                ArenaBotAi.DEFAULT_REACTION_MS);
        return dto(room, room.a);
    }

    /** Cup-Kampf gegen einen vorgegebenen Gegner, ohne Liga-Punkte. @return Raum-ID */
    public String createCup(String username, Long beybladeId, FighterSnapshot bot, double skill, int reactionMs) {
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);
        return newRoom(Mode.BOT, true, me.getUsername(), FighterSnapshot.of(me, mine),
                battles.botUser().getUsername(), bot, skill, reactionMs).id;
    }

    /** Echtzeit-Kampf zwischen zwei Spielern (Spielersuche, Lobby, Freunde). @return Raum-ID */
    public String createPvp(String userA, Long beyA, String userB, Long beyB) {
        if (userA.equals(userB)) throw bad("Ein Kampf braucht zwei Spieler.");
        User a = user(userA);
        User b = user(userB);
        return newRoom(Mode.PVP, false, a.getUsername(), FighterSnapshot.of(a, beyblades.owned(a, beyA)),
                b.getUsername(), FighterSnapshot.of(b, beyblades.owned(b, beyB)), 0, 0).id;
    }

    private Room newRoom(Mode mode, boolean cup, String userA, FighterSnapshot a, String userB, FighterSnapshot b,
                         double botSkill, int reactionMs) {
        cleanUp();
        // offene Räume desselben Spielers aufgeben (sonst könnte man drohenden Niederlagen ausweichen)
        for (Room r : List.copyOf(rooms.values())) {
            if (r.status != Status.DONE && (r.a.username.equals(userA) || r.b.username.equals(userA))) {
                synchronized (r) {
                    abandon(r, r.a.username.equals(userA) ? Side.A : Side.B);
                }
            }
        }
        List<Arena> all = Arena.all();
        Arena arena = all.get(random.nextInt(all.size()));
        LaunchChallenge challenge = LaunchChallenge.random(random);
        Room room = new Room(UUID.randomUUID().toString(), mode, cup, arena, BattleService.selectVariant(arena, null),
                challenge, new Seat(Side.A, userA, a, true), new Seat(Side.B, userB, b, mode == Mode.PVP), now());
        if (mode == Mode.BOT) {
            room.b.launch = challenge.score(BotLauncher.input(challenge, random, botSkill));
            room.botReactionMs = reactionMs;
        }
        rooms.put(room.id, room);
        return room;
    }

    // ---------------------------------------------------------------- Abfragen und Anspinnen

    public ArenaRoomDto get(String id, String username) {
        Room room = room(id);
        synchronized (room) {
            return dto(room, room.seatOf(username));
        }
    }

    public ArenaRoomDto launch(String id, String username, LaunchInput input) {
        Room room = room(id);
        synchronized (room) {
            Seat me = room.seatOf(username);
            if (room.status != Status.LAUNCH) throw conflict("Schon angespinnt.");
            if (me.launch == null) me.launch = room.challenge.score(input == null ? LaunchInput.NONE : input);
            maybeStart(room);
            return dto(room, me);
        }
    }

    /** Gewinner-Seite eines entschiedenen Raums, {@code null} solange offen (Cup). */
    public Side winnerIfDone(String id) {
        Room room = rooms.get(id);
        if (room == null) return null;
        synchronized (room) {
            return room.result == null ? null : room.result.winner();
        }
    }

    public String finishIfDone(String id) {
        Room room = rooms.get(id);
        if (room == null) return null;
        synchronized (room) {
            return room.result == null ? null : room.result.finish().name();
        }
    }

    // ---------------------------------------------------------------- WebSocket

    /** Verbindung eines Spielers: Er bekommt ab jetzt den Stand. */
    public void attach(String id, String username, WebSocketSession session) {
        Room room = room(id);
        synchronized (room) {
            Seat seat = room.seatOf(username);
            seat.session = session;
            seat.disconnectedAt = -1;
            send(seat, status(room, seat));
        }
    }

    public void detach(String id, WebSocketSession session) {
        Room room = rooms.get(id);
        if (room == null) return;
        synchronized (room) {
            for (Seat s : List.of(room.a, room.b)) {
                if (s.session == session) {
                    s.session = null;
                    s.disconnectedAt = now();
                }
            }
        }
    }

    /** Eingabe aus dem Browser: Richtung gilt bis zur nächsten, Dash und Ult als einmaliger Druck. */
    public void input(String id, String username, double x, double z, boolean dash, boolean ult) {
        Room room = rooms.get(id);
        if (room == null) return;
        synchronized (room) {
            Seat seat = room.seatOf(username);
            if (!Double.isFinite(x) || !Double.isFinite(z)) return;
            double len = Math.hypot(x, z);
            if (len > 1) {
                x /= len;
                z /= len;
            }
            seat.dir = new Input(x, z, false, false);
            if (dash) seat.dashPressed = true;
            if (ult) seat.ultPressed = true;
        }
    }

    // ---------------------------------------------------------------- Takt

    void tickAll() {
        long now = now();
        for (Room room : List.copyOf(rooms.values())) {
            try {
                synchronized (room) {
                    tick(room, now);
                }
            } catch (RuntimeException e) {
                log.warn("Arena-Raum {}: Fehler im Takt", room.id, e);
            }
        }
    }

    /** Raum bis {@code now} (ms) weiterrechnen und den Stand verschicken. Für Tests auch direkt aufrufbar. */
    void tick(Room room, long now) {
        switch (room.status) {
            case LAUNCH -> {
                if (now - room.createdAt > LaunchRules.SUBMIT_TIMEOUT_MS) {
                    if (room.a.launch == null) room.a.launch = Launch.MISSED;
                    if (room.b.launch == null) room.b.launch = Launch.MISSED;
                    maybeStart(room);
                }
            }
            case COUNTDOWN -> {
                if (now >= room.fightAt) {
                    room.status = Status.FIGHT;
                    room.lastStep = now;
                    // wer nie verbunden war, bekommt dieselbe Gnadenfrist wie bei einem Abbruch
                    for (Seat s : List.of(room.a, room.b)) {
                        if (s.human && s.session == null && s.disconnectedAt < 0) s.disconnectedAt = now;
                    }
                }
                broadcast(room, now, false);
            }
            case FIGHT -> fight(room, now);
            case DONE -> { }
        }
    }

    private void fight(Room room, long now) {
        // Verbindung verloren: nach einer Gnadenfrist verloren
        for (Seat s : List.of(room.a, room.b)) {
            if (s.human && s.session == null && s.disconnectedAt >= 0 && now - s.disconnectedAt > DISCONNECT_MS) {
                room.sim.forfeit(s.side);
                collect(room, now);
                finish(room, now);
                return;
            }
        }
        if (now < room.frozenUntil) {
            room.lastStep = now;
            broadcast(room, now, false);
            return;
        }
        double stepMs = 1000.0 / ArenaPhysics.SIM_HZ;
        room.acc += Math.min(250, now - room.lastStep);
        room.lastStep = now;
        while (room.acc >= stepMs && room.sim.finished() == null && now >= room.frozenUntil) {
            Input inA = input(room, room.a);
            Input inB = input(room, room.b);
            room.sim.step(inA, inB);
            room.acc -= stepMs;
            collect(room, now);
        }
        if (room.sim.finished() != null) {
            finish(room, now);
            return;
        }
        broadcast(room, now, false);
    }

    private Input input(Room room, Seat s) {
        if (!s.human) return room.bot.think(room.sim);
        Input in = new Input(s.dir.x(), s.dir.z(), s.dashPressed, s.ultPressed);
        s.dashPressed = false;
        s.ultPressed = false;
        return in;
    }

    /** Ereignisse sammeln; eine Ult friert den Kampf kurz für beide ein. */
    private void collect(Room room, long now) {
        for (Event e : room.sim.drain()) {
            room.events.add(e);
            if (e.type() == EventType.ULT) room.frozenUntil = now + ULT_FREEZE_MS;
        }
    }

    private void finish(Room room, long now) {
        if (room.status == Status.DONE) return;
        var r = room.sim.finished();
        room.status = Status.DONE;
        room.result = r;
        room.finishedAt = now;
        try {
            room.booked = ranking.apply(room.a.username, room.mode == Mode.PVP ? room.b.username : null, r.winner(),
                    !room.cup);
        } catch (RuntimeException e) {
            log.warn("Arena-Raum {}: Wertung fehlgeschlagen", room.id, e);
        }
        broadcast(room, now, true);
        for (Seat s : List.of(room.a, room.b)) send(s, status(room, s));
    }

    /** Aufgeben (neuer Raum, Verbindung weg in der Startphase): als Niederlage werten, wenn der Kampf lief. */
    private void abandon(Room room, Side loser) {
        if (room.status == Status.DONE) return;
        if (room.status == Status.FIGHT || room.status == Status.COUNTDOWN) {
            if (room.sim == null) startSim(room);
            room.sim.forfeit(loser);
            collect(room, now());
            finish(room, now());
        } else {
            room.status = Status.DONE;
            room.finishedAt = now();
        }
    }

    private void maybeStart(Room room) {
        if (room.status != Status.LAUNCH || room.a.launch == null || room.b.launch == null) return;
        startSim(room);
        room.status = Status.COUNTDOWN;
        room.fightAt = now() + COUNTDOWN_MS;
    }

    private void startSim(Room room) {
        if (room.sim != null) return;
        long seed = random.nextLong();
        room.sim = new ArenaPhysics(ArenaGeometry.of(room.arena), setup(room.a), setup(room.b), seed);
        if (!room.b.human) {
            Archetype type = StyleVector.of(room.b.snapshot.stats()).archetype();
            room.bot = new ArenaBotAi(Side.B, type, seed + 17, room.botReactionMs);
        }
    }

    private static FighterSetup setup(Seat s) {
        return new FighterSetup(s.snapshot.stats(), s.snapshot.fighter().ult(),
                s.launch == null ? 0 : s.launch.quality());
    }

    // ---------------------------------------------------------------- Nachrichten

    /** Stand an beide (gesammelte Ereignisse seit dem letzten Mal). */
    private void broadcast(Room room, long now, boolean force) {
        if (!force && now - room.lastBroadcast < BROADCAST_MS) return;
        room.lastBroadcast = now;
        Map<String, Object> msg = state(room, now);
        room.events.clear();
        String text;
        try {
            text = json.writeValueAsString(msg);
        } catch (IOException e) {
            return;
        }
        for (Seat s : List.of(room.a, room.b)) sendRaw(s, text);
    }

    private Map<String, Object> state(Room room, long now) {
        ArenaPhysics sim = room.sim;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", "state");
        m.put("status", room.status.name());
        m.put("t", sim == null ? 0 : sim.timeMs());
        m.put("countdownMs", room.status == Status.COUNTDOWN ? Math.max(0, room.fightAt - now) : 0);
        m.put("frozen", now < room.frozenUntil);
        if (sim != null) {
            m.put("a", body(sim, Side.A));
            m.put("b", body(sim, Side.B));
        }
        m.put("events", List.copyOf(room.events));
        return m;
    }

    private static Map<String, Object> body(ArenaPhysics sim, Side side) {
        ArenaPhysics.Body b = sim.body(side);
        int t = sim.timeMs();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("x", round(b.x));
        m.put("z", round(b.z));
        m.put("vx", round(b.vx));
        m.put("vz", round(b.vz));
        m.put("spin", round(Math.max(0, b.spin)));
        m.put("meter", round(b.meter));
        m.put("ult", sim.isUltActive(side) ? b.ult.name() : null);
        m.put("ultReady", sim.isUltReady(side));
        m.put("dash", round(Math.min(1, 1 - (b.dashReadyAt - t) / (double) ArenaPhysics.DASH_COOLDOWN_MS)));
        m.put("hits", b.hits);
        m.put("dashes", b.dashes);
        m.put("out", b.out);
        return m;
    }

    private Map<String, Object> status(Room room, Seat seat) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", "room");
        m.put("room", dto(room, seat));
        return m;
    }

    private void send(Seat s, Map<String, Object> msg) {
        try {
            sendRaw(s, json.writeValueAsString(msg));
        } catch (IOException e) {
            // Nachricht nicht serialisierbar: ignorieren
        }
    }

    private void sendRaw(Seat s, String text) {
        WebSocketSession session = s.session;
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(text));
        } catch (IOException | IllegalStateException e) {
            s.session = null;
            s.disconnectedAt = now();
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // ---------------------------------------------------------------- Ausgabe

    private ArenaRoomDto dto(Room room, Seat me) {
        Seat them = me == room.a ? room.b : room.a;
        BattleMode mode = room.mode == Mode.PVP ? BattleMode.PVP : BattleMode.BOT;
        ArenaRoomDto.Result result = null;
        if (room.result != null) {
            ArenaResultDto mine = room.booked == null ? null : room.booked.get(me.side);
            result = new ArenaRoomDto.Result(room.result.winner().name(), room.result.finish().name(),
                    room.result.t(), mine);
        }
        long left = room.status == Status.LAUNCH
                ? Math.max(0, LaunchRules.SUBMIT_TIMEOUT_MS - (now() - room.createdAt)) : 0;
        return new ArenaRoomDto(room.id, room.mode.name(), room.cup, room.status.name(), me.side.name(),
                ArenaDto.from(room.arena, room.variant),
                List.of(battles.fighterDto(Side.A, room.a.snapshot, mode),
                        battles.fighterDto(Side.B, room.b.snapshot, mode)),
                LaunchChallengeDto.from(room.challenge), me.launch != null, them.launch != null, left, result);
    }

    // ---------------------------------------------------------------- Hilfen

    Room room(String id) {
        Room room = id == null ? null : rooms.get(id);
        if (room == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Raum nicht gefunden.");
        return room;
    }

    private void cleanUp() {
        long now = now();
        rooms.values().removeIf(r -> r.status == Status.DONE
                ? now - r.finishedAt > RETENTION_MS
                : now - r.createdAt > RETENTION_MS + LaunchRules.SUBMIT_TIMEOUT_MS);
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }

    static long now() {
        return System.nanoTime() / 1_000_000;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    // ---------------------------------------------------------------- Zustand

    static final class Room {
        final String id;
        final Mode mode;
        final boolean cup;
        final Arena arena;
        final String variant;
        final LaunchChallenge challenge;
        final Seat a;
        final Seat b;
        final long createdAt;
        Status status = Status.LAUNCH;
        ArenaPhysics sim;
        ArenaBotAi bot;
        int botReactionMs = ArenaBotAi.DEFAULT_REACTION_MS;
        long fightAt;
        long lastStep;
        double acc;
        long frozenUntil;
        long lastBroadcast;
        long finishedAt;
        final List<Event> events = new ArrayList<>();
        ArenaPhysics.Result result;
        Map<Side, ArenaResultDto> booked;

        Room(String id, Mode mode, boolean cup, Arena arena, String variant, LaunchChallenge challenge, Seat a,
             Seat b, long createdAt) {
            this.id = id;
            this.mode = mode;
            this.cup = cup;
            this.arena = arena;
            this.variant = variant;
            this.challenge = challenge;
            this.a = a;
            this.b = b;
            this.createdAt = createdAt;
        }

        /** Platz des Spielers. Fremde bekommen 404. */
        Seat seatOf(String username) {
            if (a.username.equals(username)) return a;
            if (b.username.equals(username) && mode == Mode.PVP) return b;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Raum nicht gefunden.");
        }
    }

    static final class Seat {
        final Side side;
        final String username;
        final FighterSnapshot snapshot;
        final boolean human;
        Launch launch;
        volatile WebSocketSession session;
        long disconnectedAt = -1;
        Input dir = Input.NONE;
        boolean dashPressed;
        boolean ultPressed;

        Seat(Side side, String username, FighterSnapshot snapshot, boolean human) {
            this.side = side;
            this.username = username;
            this.snapshot = snapshot;
            this.human = human;
        }
    }
}
