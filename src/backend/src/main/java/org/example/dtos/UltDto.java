package org.example.dtos;

import org.example.game.UltType;

/** Ult für die Anzeige: Typ und deutscher Name. */
public record UltDto(UltType type, String label) {

    public static UltDto of(UltType ult) {
        return ult == null ? null : new UltDto(ult, ult.label());
    }
}
