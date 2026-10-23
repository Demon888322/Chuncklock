package com.chunklock.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClaimFlagTest {
    @Test
    void parsesNamesCaseInsensitivelyAndNormalizesAliases() {
        assertEquals(ClaimFlag.PVP, ClaimFlag.fromCommand("PVP"));
        assertEquals(ClaimFlag.CONTAINER_ACCESS, ClaimFlag.fromCommand("chest-access"));
        assertEquals(ClaimFlag.CONTAINER_ACCESS, ClaimFlag.fromCommand("container_access"));
        assertEquals(ClaimFlag.CONTAINER_ACCESS, ClaimFlag.fromCommand("containers"));
        assertEquals(ClaimFlag.MOB_SPAWNING, ClaimFlag.fromCommand("mob-spawning"));
    }

    @Test
    void rejectsUnknownNames() {
        assertNull(ClaimFlag.fromCommand("unknown"));
    }
}
