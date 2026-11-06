package org.example.game;

/** Seite eines Kämpfers. A ist immer der Herausforderer, B der Gegner. */
public enum Side {
    A,
    B;

    public Side other() {
        return this == A ? B : A;
    }
}
