package org.example.controller;

import org.example.dtos.PartDto;
import org.example.repositories.TipRepository;
import org.example.services.PartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parts")
public class PartController {

    private final PartService parts;

    public PartController(PartService parts) {
        this.parts = parts;
    }

    @GetMapping("/fusion-wheels")
    public List<PartDto> fusionWheels() {
        return parts.allFusionWheels().stream().map(PartDto::from).toList();
    }

    @GetMapping("/spin-tracks")
    public List<PartDto> spinTracks() {
        return parts.allSpinTracks().stream().map(PartDto::from).toList();
    }

    @GetMapping("/tips")
    public List<PartDto> tips() {
        return parts.allTips().stream().map(PartDto::from).toList();
    }
}