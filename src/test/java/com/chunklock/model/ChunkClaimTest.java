package com.chunklock.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkClaimTest {
    @Test
    void defaultsFlagsToFalseAndOwnerIsAlwaysTrusted() {
        UUID owner = UUID.randomUUID();
        ChunkClaim claim = ChunkClaim.create(new ChunkKey(UUID.randomUUID(), -2, 4), owner);

        assertTrue(claim.isTrusted(owner));
        for (ClaimFlag flag : ClaimFlag.values()) {
            assertFalse(claim.hasFlag(flag));
        }
    }

    @Test
    void copiesInputsAndExposesUnmodifiableViews() {
        UUID member = UUID.randomUUID();
        Set<UUID> members = new HashSet<>(Set.of(member));
        EnumMap<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);
        flags.put(ClaimFlag.PVP, true);
        ChunkClaim claim = new ChunkClaim(new ChunkKey(UUID.randomUUID(), 1, 2), UUID.randomUUID(),
                members, flags, 10L, 20L);
        members.clear();
        flags.put(ClaimFlag.PVP, false);

        assertTrue(claim.isTrusted(member));
        assertTrue(claim.hasFlag(ClaimFlag.PVP));
        assertThrows(UnsupportedOperationException.class, () -> claim.trustedMembers().clear());
        assertThrows(UnsupportedOperationException.class, () -> claim.flags().clear());
    }

    @Test
    void copyIsIndependentAndTrustChangesAreReported() {
        ChunkClaim original = ChunkClaim.create(new ChunkKey(UUID.randomUUID(), 0, 0), UUID.randomUUID());
        UUID member = UUID.randomUUID();
        ChunkClaim copy = original.copy();

        assertTrue(original.addTrusted(member));
        assertFalse(original.addTrusted(member));
        original.setFlag(ClaimFlag.EXPLOSIONS, true);
        original.setLastOwnerLogin(123L);
        assertFalse(copy.isTrusted(member));
        assertFalse(copy.hasFlag(ClaimFlag.EXPLOSIONS));
        assertNotEquals(123L, copy.lastOwnerLogin());
        assertTrue(original.removeTrusted(member));
        assertFalse(original.removeTrusted(member));
    }

    @Test
    void includesAllFlagsInReturnedMap() {
        ChunkClaim claim = new ChunkClaim(new ChunkKey(UUID.randomUUID(), 0, 0), UUID.randomUUID(),
                Set.of(), Map.of(), 1L, 2L);

        assertEquals(Set.of(ClaimFlag.values()), claim.flags().keySet());
    }
}
