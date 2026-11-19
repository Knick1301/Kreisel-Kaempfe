package org.example.services;

import org.example.dtos.FriendsDto;
import org.example.dtos.FriendsDto.ChallengeDto;
import org.example.dtos.FriendsDto.Friend;
import org.example.entities.Friendship;
import org.example.entities.User;
import org.example.repositories.FriendshipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freunde: Anfragen, Annehmen, Entfernen und direkte Herausforderungen. Eine angenommene Herausforderung startet
 * ein PvP-Match (klassisch) bzw. einen Arena-Raum, beide Spieler springen per Polling hinein.
 */
@Service
public class FriendService {

    /** So lange bleibt eine Herausforderung offen */
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    /** So lange bleibt eine angenommene Herausforderung sichtbar, damit der Herausforderer hineinspringt */
    static final Duration ACCEPTED_KEEP = Duration.ofMinutes(2);

    public enum Mode { CLASSIC, ARENA }

    /** Startet einen Kampf zwischen zwei Freunden, liefert Match- bzw. Raum-ID. */
    public interface ArenaStarter {
        String startPvp(String userA, Long beyA, String userB, Long beyB);
    }

    private final FriendshipRepository friendships;
    private final UserService users;
    private final BeybladeService beyblades;
    private final MatchService matches;
    private final PresenceService presence;
    private final RankingService ranking;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private ArenaStarter arena;
    private Clock clock = Clock.systemUTC();

    public FriendService(FriendshipRepository friendships, UserService users, BeybladeService beyblades,
                         MatchService matches, PresenceService presence, RankingService ranking) {
        this.friendships = friendships;
        this.users = users;
        this.beyblades = beyblades;
        this.matches = matches;
        this.presence = presence;
        this.ranking = ranking;
    }

    /** Arena-Räume melden sich hier an, sobald es sie gibt (vermeidet eine zyklische Abhängigkeit). */
    public void setArenaStarter(ArenaStarter arena) {
        this.arena = arena;
    }

    // ---------------------------------------------------------------- Freundschaften

    @Transactional(readOnly = true)
    public FriendsDto list(String username) {
        List<Friend> friends = new ArrayList<>();
        List<Friend> incoming = new ArrayList<>();
        List<Friend> outgoing = new ArrayList<>();
        for (Friendship f : friendships.involving(username)) {
            Friend dto = friend(f, f.other(username));
            if (f.getStatus() == Friendship.Status.ACCEPTED) friends.add(dto);
            else if (f.getAddressee().getUsername().equals(username)) incoming.add(dto);
            else outgoing.add(dto);
        }
        // online zuerst, dann alphabetisch
        friends.sort(Comparator.comparing(Friend::online).reversed().thenComparing(Friend::username,
                String.CASE_INSENSITIVE_ORDER));
        return new FriendsDto(friends, incoming, outgoing, challengesOf(username));
    }

    /** Anfrage senden. Hat der andere mich schon angefragt, sind wir sofort befreundet. */
    @Transactional
    public FriendsDto request(String username, String otherName) {
        String name = otherName == null ? "" : otherName.trim();
        if (name.equalsIgnoreCase(username)) throw bad("Du kannst dich nicht selbst hinzufügen.");
        User me = users.get(username);
        User other = users.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Spieler " + name + " gibt es nicht."));
        if (other.getUsername().equals(username)) throw bad("Du kannst dich nicht selbst hinzufügen.");
        var existing = friendships.between(username, other.getUsername());
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == Friendship.Status.ACCEPTED) throw conflict("Ihr seid schon befreundet.");
            if (f.getRequester().getUsername().equals(username)) throw conflict("Anfrage ist schon unterwegs.");
            f.accept();
            friendships.save(f);
        } else {
            friendships.save(new Friendship(me, other));
        }
        return list(username);
    }

    @Transactional
    public FriendsDto accept(String username, Long id) {
        Friendship f = own(username, id);
        if (!f.getAddressee().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nur der Empfänger kann annehmen.");
        }
        f.accept();
        friendships.save(f);
        return list(username);
    }

    /** Anfrage ablehnen oder zurückziehen, Freundschaft beenden. */
    @Transactional
    public FriendsDto remove(String username, Long id) {
        friendships.delete(own(username, id));
        return list(username);
    }

    // ---------------------------------------------------------------- Herausforderungen

    @Transactional(readOnly = true)
    public FriendsDto challenge(String username, String otherName, Long beybladeId, String modeName) {
        Mode mode = mode(modeName);
        if (mode == Mode.ARENA && arena == null) throw bad("Arena-Herausforderungen sind noch nicht verfügbar.");
        Friendship f = friendships.between(username, otherName)
                .filter(x -> x.getStatus() == Friendship.Status.ACCEPTED)
                .orElseThrow(() -> bad("Herausfordern geht nur unter Freunden."));
        String other = f.other(username).getUsername();
        beyblades.owned(users.get(username), beybladeId);
        cleanUp();
        boolean open = challenges.values().stream().anyMatch(c -> c.matchId == null
                && ((c.from.equals(username) && c.to.equals(other)) || (c.from.equals(other) && c.to.equals(username))));
        if (open) throw conflict("Zwischen euch ist schon eine Herausforderung offen.");
        Challenge c = new Challenge(UUID.randomUUID().toString(), username, beybladeId, other, mode, clock.instant());
        challenges.put(c.id, c);
        return list(username);
    }

    /** Herausforderung annehmen: Der Kampf startet, beide sehen die Match-ID. */
    @Transactional(readOnly = true)
    public FriendsDto acceptChallenge(String username, String id, Long beybladeId) {
        cleanUp();
        Challenge c = challenges.get(id);
        if (c == null || !c.to.equals(username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Herausforderung nicht gefunden.");
        }
        synchronized (c) {
            if (c.matchId == null) {
                beyblades.owned(users.get(username), beybladeId);
                c.matchId = c.mode == Mode.ARENA
                        ? arena.startPvp(c.from, c.fromBey, username, beybladeId)
                        : matches.createPvpMatch(c.from, c.fromBey, username, beybladeId, null);
                c.acceptedAt = clock.instant();
            }
        }
        return list(username);
    }

    public FriendsDto declineChallenge(String username, String id) {
        Challenge c = challenges.get(id);
        if (c != null && c.matchId == null && (c.to.equals(username) || c.from.equals(username))) {
            challenges.remove(id);
        }
        return list(username);
    }

    private List<ChallengeDto> challengesOf(String username) {
        cleanUp();
        Instant now = clock.instant();
        return challenges.values().stream()
                .filter(c -> c.from.equals(username) || c.to.equals(username))
                .sorted(Comparator.comparing(c -> c.createdAt))
                .map(c -> new ChallengeDto(c.id, c.from, c.to, c.from.equals(username), c.mode.name(), c.matchId,
                        Math.max(0, Duration.between(now, c.createdAt.plus(CHALLENGE_TTL)).toMillis())))
                .toList();
    }

    private void cleanUp() {
        Instant now = clock.instant();
        challenges.values().removeIf(c -> c.matchId == null
                ? now.isAfter(c.createdAt.plus(CHALLENGE_TTL))
                : now.isAfter(c.acceptedAt.plus(ACCEPTED_KEEP)));
    }

    // ---------------------------------------------------------------- Hilfen

    private Friend friend(Friendship f, User other) {
        return new Friend(f.getId(), other.getUsername(), presence.online(other.getUsername()),
                ranking.rankOf(other), ArenaService.rankOf(other));
    }

    private Friendship own(String username, Long id) {
        Friendship f = friendships.findById(id).orElse(null);
        if (f == null || !(f.getRequester().getUsername().equals(username)
                || f.getAddressee().getUsername().equals(username))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freundschaft nicht gefunden.");
        }
        return f;
    }

    private static Mode mode(String name) {
        if (name == null || name.isBlank()) return Mode.CLASSIC;
        try {
            return Mode.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Unbekannter Modus " + name + ".");
        }
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static final class Challenge {
        final String id;
        final String from;
        final Long fromBey;
        final String to;
        final Mode mode;
        final Instant createdAt;
        volatile String matchId;
        volatile Instant acceptedAt;

        Challenge(String id, String from, Long fromBey, String to, Mode mode, Instant createdAt) {
            this.id = id;
            this.from = from;
            this.fromBey = fromBey;
            this.to = to;
            this.mode = mode;
            this.createdAt = createdAt;
        }
    }
}
