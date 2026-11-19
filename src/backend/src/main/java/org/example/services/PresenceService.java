package org.example.services;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Wer ist gerade online: letzte Anfrage pro Spieler, nur im Speicher (kein Datenbank-Schreiben pro Anfrage). */
@Service
public class PresenceService {

    /** So lange gilt man nach der letzten Anfrage als online */
    public static final Duration ONLINE = Duration.ofSeconds(60);

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public void seen(String username) {
        lastSeen.put(username, clock.instant());
    }

    public boolean online(String username) {
        Instant t = lastSeen.get(username);
        return t != null && Duration.between(t, clock.instant()).compareTo(ONLINE) <= 0;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
