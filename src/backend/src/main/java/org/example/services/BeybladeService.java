package org.example.services;

import org.example.dtos.BeybladeRequest;
import org.example.dtos.BeybladeStats;
import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.repositories.BeybladeRepository;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BeybladeService {

    public static final int MAX_NAME_LENGTH = 24;

    private final PartService parts;
    private final UserRepository users;
    private final BeybladeRepository beyblades;

    public BeybladeService(PartService parts, UserRepository users, BeybladeRepository beyblades) {
        this.parts = parts;
        this.users = users;
        this.beyblades = beyblades;
    }

    public List<Beyblade> forUser(String username) {
        return beyblades.findByOwnerId(user(username).getId());
    }

    public BeybladeStats preview(Long fusionWheelId, Long spinTrackId, Long tipId) {
        Beyblade b = new Beyblade();
        b.setFusionWheel(parts.getFusionWheel(fusionWheelId));
        b.setSpinTrack(parts.getSpinTrack(spinTrackId));
        b.setTip(parts.getTip(tipId));
        return b.getStats();
    }

    @Transactional
    public Beyblade create(String username, BeybladeRequest request) {
        User owner = user(username);
        Beyblade b = new Beyblade();
        apply(b, owner, request);
        owner.addBeyblade(b);
        return beyblades.save(b);
    }

    @Transactional
    public Beyblade update(String username, Long id, BeybladeRequest request) {
        User owner = user(username);
        Beyblade b = owned(owner, id);
        apply(b, owner, request);
        return beyblades.save(b);
    }

    @Transactional
    public void delete(String username, Long id) {
        User owner = user(username);
        Beyblade b = owned(owner, id);
        owner.removeBeyblade(b);
        beyblades.delete(b);
    }

    public Beyblade owned(User owner, Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Kreisel wählen.");
        }
        return beyblades.findById(id)
                .filter(b -> b.getOwner() != null && b.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Kreisel " + id + " nicht gefunden"));
    }

    private void apply(Beyblade b, User owner, BeybladeRequest request) {
        if (request == null || request.fusionWheelId() == null || request.spinTrackId() == null
                || request.tipId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte alle drei Teile wählen.");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Name muss 1–" + MAX_NAME_LENGTH + " Zeichen lang sein.");
        }
        beyblades.findByOwnerIdAndName(owner.getId(), name)
                .filter(other -> !other.getId().equals(b.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Du hast schon einen Kreisel namens \"" + name + "\".");
                });
        b.setName(name);
        b.setFusionWheel(parts.getFusionWheel(request.fusionWheelId()));
        b.setSpinTrack(parts.getSpinTrack(request.spinTrackId()));
        b.setTip(parts.getTip(request.tipId()));
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }
}
