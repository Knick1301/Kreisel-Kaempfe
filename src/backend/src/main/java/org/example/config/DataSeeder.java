package org.example.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.Part;
import org.example.entities.parts.SpinTrack;
import org.example.entities.parts.Tip;
import org.example.repositories.FusionWheelRepository;
import org.example.repositories.SpinTrackRepository;
import org.example.repositories.TipRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private record WheelData(String name, String modelPath, int weight,
                             int attack, int defense, int burstResistance) {}
    private record TrackData(String name, String modelPath, int weight,
                             int defense, int stamina) {}
    private record TipData(String name, String modelPath, int weight,
                           int stamina, int dash) {}

    private final FusionWheelRepository wheels;
    private final SpinTrackRepository tracks;
    private final TipRepository tips;
    private final ObjectMapper objectMapper;

    public DataSeeder(FusionWheelRepository wheels, SpinTrackRepository tracks,
                      TipRepository tips, ObjectMapper objectMapper) {
        this.wheels = wheels;
        this.tracks = tracks;
        this.tips = tips;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        List<WheelData> wheelData = read("parts/fusion-wheels.json", new TypeReference<List<WheelData>>() {});
        for (WheelData d : wheelData) {
            FusionWheel w = wheels.findByName(d.name()).orElseGet(FusionWheel::new);
            w.setName(d.name());
            w.setModelPath(d.modelPath());
            w.setWeight(d.weight());
            w.setAttack(d.attack());
            w.setDefense(d.defense());
            w.setBurstResistance(d.burstResistance());
            w.setActive(true);
            wheels.save(w);
        }
        deactivateMissing(wheels, wheelData.stream().map(WheelData::name).toList());

        List<TrackData> trackData = read("parts/spin-tracks.json", new TypeReference<List<TrackData>>() {});
        for (TrackData d : trackData) {
            SpinTrack t = tracks.findByName(d.name()).orElseGet(SpinTrack::new);
            t.setName(d.name());
            t.setModelPath(d.modelPath());
            t.setWeight(d.weight());
            t.setDefense(d.defense());
            t.setStamina(d.stamina());
            t.setActive(true);
            tracks.save(t);
        }
        deactivateMissing(tracks, trackData.stream().map(TrackData::name).toList());

        List<TipData> tipData = read("parts/tips.json", new TypeReference<List<TipData>>() {});
        for (TipData d : tipData) {
            Tip t = tips.findByName(d.name()).orElseGet(Tip::new);
            t.setName(d.name());
            t.setModelPath(d.modelPath());
            t.setWeight(d.weight());
            t.setStamina(d.stamina());
            t.setDash(d.dash());
            t.setActive(true);
            tips.save(t);
        }
        deactivateMissing(tips, tipData.stream().map(TipData::name).toList());
    }

    private <T extends Part> void deactivateMissing(JpaRepository<T, Long> repo, List<String> namesInJson) {
        for (T part : repo.findAll()) {
            if (part.isActive() && !namesInJson.contains(part.getName())) {
                part.setActive(false);
                repo.save(part);
            }
        }
    }

    private <T> List<T> read(String path, TypeReference<List<T>> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }
}