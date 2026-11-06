package org.example.services;

import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.Part;
import org.example.entities.parts.SpinTrack;
import org.example.entities.parts.Tip;
import org.example.repositories.FusionWheelRepository;
import org.example.repositories.SpinTrackRepository;
import org.example.repositories.TipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class PartService {

    private final FusionWheelRepository wheels;
    private final SpinTrackRepository spinTracks;
    private final TipRepository tips;

    public PartService(FusionWheelRepository wheels,
                       SpinTrackRepository spinTracks,
                       TipRepository tips) {
        this.wheels = wheels;
        this.spinTracks = spinTracks;
        this.tips = tips;
    }

    public List<FusionWheel> allFusionWheels() { return wheels.findByActiveTrue(); }
    public List<SpinTrack>   allSpinTracks()   { return spinTracks.findByActiveTrue(); }
    public List<Tip>         allTips()         { return tips.findByActiveTrue(); }

    public FusionWheel getFusionWheel(Long id) {
        return wheels.findById(id).orElseThrow(() -> notFound("Fusion Wheel", id));
    }

    public SpinTrack getSpinTrack(Long id) {
        return spinTracks.findById(id).orElseThrow(() -> notFound("Spin Track", id));
    }

    public Tip getTip(Long id) {
        return tips.findById(id).orElseThrow(() -> notFound("Tip", id));
    }

    /** Die drei Teile eines gespeicherten Kreisels, leer wenn eine ID fehlt. */
    public Optional<List<Part>> resolve(Long fusionWheelId, Long spinTrackId, Long tipId) {
        if (fusionWheelId == null || spinTrackId == null || tipId == null) return Optional.empty();
        Optional<FusionWheel> wheel = wheels.findById(fusionWheelId);
        Optional<SpinTrack> track = spinTracks.findById(spinTrackId);
        Optional<Tip> tip = tips.findById(tipId);
        if (wheel.isEmpty() || track.isEmpty() || tip.isEmpty()) return Optional.empty();
        return Optional.of(List.of(wheel.get(), track.get(), tip.get()));
    }

    private ResponseStatusException notFound(String type, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " mit ID " + id + " nicht gefunden");
    }
}