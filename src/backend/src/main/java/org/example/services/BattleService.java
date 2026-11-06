package org.example.services;

import org.example.config.SystemUserSeeder;
import org.example.dtos.ArenaDto;
import org.example.dtos.BattleReportDto;
import org.example.dtos.FighterDto;
import org.example.dtos.MatchupDto;
import org.example.dtos.PartDto;
import org.example.dtos.StyleDto;
import org.example.dtos.TimelineDto;
import org.example.entities.BattleResult;
import org.example.entities.Beyblade;
import org.example.entities.FighterSnapshot;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleMode;
import org.example.game.BattleOutcome;
import org.example.game.BattleTuning;
import org.example.game.BotFactory;
import org.example.game.Side;
import org.example.game.StyleVector;
import org.example.repositories.BattleResultRepository;
import org.example.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    /** Seeds bleiben unter 2^53, damit sie in JavaScript exakt darstellbar sind. */
    private static final long SEED_MASK = (1L << 53) - 1;

    private final BattleResultRepository results;
    private final UserRepository users;
    private final BeybladeService beyblades;
    private final PartService parts;
    private final RankingService ranking;
    private final Random random = new Random();

    public BattleService(BattleResultRepository results, UserRepository users, BeybladeService beyblades,
                         PartService parts, RankingService ranking) {
        this.results = results;
        this.users = users;
        this.beyblades = beyblades;
        this.parts = parts;
        this.ranking = ranking;
    }

    /** Kampf zwischen zwei Spielern (Schnelles Match gegen echte User, Lobby). */
    @Transactional
    public BattleResult fight(User userA, Beyblade beybladeA, User userB, Beyblade beybladeB) {
        if (userA.getId().equals(userB.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Du kannst nicht gegen dich selbst kämpfen.");
        }
        return simulateAndSave(BattleMode.PVP, Arena.standard(), userA, FighterSnapshot.of(userA, beybladeA),
                userB, FighterSnapshot.of(userB, beybladeB));
    }

    /** Kampf gegen einen KI-Gegner mit zufälligen Werten. Zählt fürs Profil, nicht für die Rangliste. */
    @Transactional
    public BattleResult botMatch(String username, Long beybladeId) {
        return botMatch(username, beybladeId, null);
    }

    /** @param arenaId Arena-ID, {@code null} für das Standard-Stadion */
    @Transactional
    public BattleResult botMatch(String username, Long beybladeId, String arenaId) {
        Arena arena = arenaId == null || arenaId.isBlank()
                ? Arena.standard()
                : Arena.find(arenaId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannte Arena \"" + arenaId + "\"."));
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);
        User bot = users.findByUsername(SystemUserSeeder.BOT_USERNAME).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gerade ist kein KI-Gegner verfügbar."));
        BotFactory.Bot generated = BotFactory.create(mine.getStats(), random);
        return simulateAndSave(BattleMode.BOT, arena, me, FighterSnapshot.of(me, mine), bot,
                FighterSnapshot.bot(bot.getUsername(), generated.name(), generated.stats(), generated.accent()));
    }

    @Transactional
    public BattleResult quickMatch(String username, Long beybladeId) {
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);

        List<User> opponents = users.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !u.getUsername().equals(SystemUserSeeder.BOT_USERNAME))
                .filter(u -> !u.getBeybladeConfigurations().isEmpty())
                .toList();
        if (opponents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gerade ist kein Gegner verfügbar.");
        }
        User opponent = opponents.get(random.nextInt(opponents.size()));
        List<Beyblade> theirs = opponent.getBeybladeConfigurations();
        return fight(me, mine, opponent, theirs.get(random.nextInt(theirs.size())));
    }

    private BattleResult simulateAndSave(BattleMode mode, Arena arena, User userA, FighterSnapshot a, User userB,
                                         FighterSnapshot b) {
        // Verwaltete Instanzen holen, damit Liga-Punkte und XP sicher mitgespeichert werden
        userA = users.findById(userA.getId()).orElseThrow();
        userB = users.findById(userB.getId()).orElseThrow();
        long seed = random.nextLong() & SEED_MASK;
        BattleOutcome outcome = new Battle(arena, a.fighter(), b.fighter(), seed).run();

        boolean aWon = outcome.winnerSide() == Side.A;
        BattleResult result = new BattleResult();
        result.setMode(mode);
        result.setWinner(aWon ? userA : userB);
        result.setLoser(aWon ? userB : userA);
        result.setWinnerBeyblade(outcome.winner().beyblade());
        result.setLoserBeyblade(outcome.loser().beyblade());
        result.setFinish(outcome.finish());
        result.setDurationMs(outcome.durationMs());
        result.setWinnerSide(outcome.winnerSide());
        result.setSeed(seed);
        result.setSimVersion(BattleTuning.SIM_VERSION);
        result.setArena(arena.id());
        result.setFighterA(a);
        result.setFighterB(b);
        ranking.apply(result);
        return results.save(result);
    }

    /**
     * Spielt einen gespeicherten Kampf mit Seed und Snapshots exakt nach. Leer, wenn das nicht geht
     * (Altbestand, andere Simulationsversion) oder das Ergebnis nicht mehr übereinstimmt. So widerspricht
     * eine Animation nie dem gespeicherten Ergebnis.
     */
    public Optional<BattleOutcome> replay(BattleResult r) {
        if (r.getSeed() == null || r.getSimVersion() == null || r.getSimVersion() != BattleTuning.SIM_VERSION
                || r.getFighterA() == null || r.getFighterB() == null
                || !r.getFighterA().isComplete() || !r.getFighterB().isComplete()) {
            return Optional.empty();
        }
        Optional<Arena> arena = Arena.find(r.getArena());
        if (arena.isEmpty()) return Optional.empty();
        BattleOutcome o = new Battle(arena.get(), r.getFighterA().fighter(), r.getFighterB().fighter(),
                r.getSeed()).run();
        if (o.winnerSide() != r.getWinnerSide() || o.finish() != r.getFinish()
                || !Integer.valueOf(o.durationMs()).equals(r.getDurationMs())) {
            log.warn("Replay von Kampf {} weicht vom gespeicherten Ergebnis ab", r.getId());
            return Optional.empty();
        }
        return Optional.of(o);
    }

    /** Vollständiger Bericht mit Kämpfern, Matchup und (wenn möglich) Timeline. */
    @Transactional(readOnly = true)
    public BattleReportDto report(Long id) {
        return report(get(id));
    }

    @Transactional(readOnly = true)
    public BattleReportDto report(BattleResult r) {
        if (r.getFighterA() == null || r.getFighterB() == null
                || !r.getFighterA().isComplete() || !r.getFighterB().isComplete()) {
            return BattleReportDto.from(r);
        }
        StyleVector styleA = StyleVector.of(r.getFighterA().stats());
        StyleVector styleB = StyleVector.of(r.getFighterB().stats());
        ArenaDto arena = Arena.find(r.getArena()).map(ArenaDto::from).orElse(null);
        TimelineDto timeline = replay(r).map(o -> TimelineDto.from(o.timeline())).orElse(null);
        return BattleReportDto.full(r, arena,
                List.of(fighterDto(Side.A, r.getFighterA(), styleA, r.getMode()),
                        fighterDto(Side.B, r.getFighterB(), styleB, r.getMode())),
                MatchupDto.of(styleA, styleB), timeline);
    }

    private FighterDto fighterDto(Side side, FighterSnapshot s, StyleVector style, BattleMode mode) {
        boolean bot = mode == BattleMode.BOT && side == Side.B;
        List<PartDto> partDtos = parts.resolve(s.getFusionWheelId(), s.getSpinTrackId(), s.getTipId())
                .map(list -> list.stream().map(PartDto::from).toList())
                .orElse(null);
        return new FighterDto(side, s.getOwner(), bot, s.getBeyblade(), s.stats(), StyleDto.from(style),
                style.archetype(), partDtos, s.getAccent());
    }

    @Transactional(readOnly = true)
    public List<BattleResult> history(String username, int limit) {
        return results.findForUser(username).stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public BattleResult get(Long id) {
        return results.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Kampf " + id + " nicht gefunden"));
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }
}
