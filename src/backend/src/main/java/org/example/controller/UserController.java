package org.example.controller;

import org.example.dtos.BeybladeDto;
import org.example.services.BeybladeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final BeybladeService beyblades;

    public UserController(BeybladeService beyblades) {
        this.beyblades = beyblades;
    }

    @GetMapping("/{username}/beyblades")
    public List<BeybladeDto> beyblades(@PathVariable String username) {
        return beyblades.forUser(username).stream().map(BeybladeDto::from).toList();
    }
}
