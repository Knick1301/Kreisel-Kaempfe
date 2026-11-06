package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enum-Spalten liegen als Text in der Datenbank, nicht als H2-ENUM. Ein ENUM kennt nur die Werte von damals,
 * dann ließ sich z. B. ein Kampf mit Ring-Out in einer älteren Datenbank nicht speichern.
 */
@SpringBootTest
class EnumColumnTest {

    @Autowired JdbcTemplate jdbc;

    private String type(String column) {
        return jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'BATTLE_RESULT' AND COLUMN_NAME = ?", String.class, column);
    }

    @Test
    void enumSpaltenSindText() {
        assertEquals("CHARACTER VARYING", type("FINISH"));
        assertEquals("CHARACTER VARYING", type("BATTLE_MODE"));
        assertEquals("CHARACTER VARYING", type("WINNER_SIDE"));
    }
}
