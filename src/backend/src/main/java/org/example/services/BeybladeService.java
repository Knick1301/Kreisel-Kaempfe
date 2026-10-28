package org.example.services;

import org.example.dtos.BeybladeStats;
import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.repositories.BeybladeRepository;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BeybladeService {

    private final PartService parts;
    private final UserRepository users;
    private final BeybladeRepository beyblades;

    public BeybladeService(PartService parts, UserRepository users, BeybladeRepository beyblades) {
        this.parts = parts;
        this.users = users;
        this.beyblades = beyblades;
    }

    public List<Beyblade> forUser(String username) {
        User user = users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
        return beyblades.findByOwnerId(user.getId());
    }

    public BeybladeStats preview(Long fusionWheelId, Long spinTrackId, Long tipId) {
        Beyblade b = new Beyblade();
        b.setFusionWheel(parts.getFusionWheel(fusionWheelId));
        b.setSpinTrack(parts.getSpinTrack(spinTrackId));
        b.setTip(parts.getTip(tipId));
        return b.getStats();
    }

}
