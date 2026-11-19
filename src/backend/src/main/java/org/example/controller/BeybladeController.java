package org.example.controller;

import org.example.dtos.BeybladeStats;
import org.example.services.BeybladeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beyblades")
public class BeybladeController {

    private final BeybladeService service;

    public BeybladeController(BeybladeService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    public BeybladeStats preview(@RequestParam Long fusionWheelId,
                                 @RequestParam Long spinTrackId,
                                 @RequestParam Long tipId) {
        return service.preview(fusionWheelId, spinTrackId, tipId);
    }
}