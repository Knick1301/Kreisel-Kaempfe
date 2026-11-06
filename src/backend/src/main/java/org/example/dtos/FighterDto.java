package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.game.Archetype;
import org.example.game.Side;

import java.util.List;

/**
 * @param parts  Fusion Wheel, Spin Track, Tip; {@code null} bei KI-Gegnern (Platzhalter-Modell)
 * @param accent Akzentfarbe des Platzhalter-Modells, nur bei KI-Gegnern
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FighterDto(Side side, String username, boolean bot, String beyblade, BeybladeStats stats,
                         StyleDto style, Archetype archetype, List<PartDto> parts, String accent) {}
