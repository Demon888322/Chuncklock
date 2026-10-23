package com.chunklock.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChunkClaim {
    private final ChunkKey key;
    private final UUID owner;
    private final Set<UUID> trustedMembers;
    private final EnumMap<ClaimFlag, Boolean> flags;
    private final long createdTimestamp;
    private long lastOwnerLogin;

    public ChunkClaim(ChunkKey key, UUID owner, Set<UUID> trustedMembers,
                      Map<ClaimFlag, Boolean> flags, long createdTimestamp, long lastOwnerLogin) {
        this.key = key;
        this.owner = owner;
        this.trustedMembers = new HashSet<>(trustedMembers);
        this.flags = new EnumMap<>(ClaimFlag.class);
        for (ClaimFlag flag : ClaimFlag.values()) {
            this.flags.put(flag, flags.getOrDefault(flag, false));
        }
        this.createdTimestamp = createdTimestamp;
        this.lastOwnerLogin = lastOwnerLogin;
    }

    public static ChunkClaim create(ChunkKey key, UUID owner) {
        return new ChunkClaim(key, owner, Set.of(), Map.of(), System.currentTimeMillis(), 0L);
    }

    public ChunkKey key() {
        return key;
    }

    public UUID owner() {
        return owner;
    }

    public Set<UUID> trustedMembers() {
        return Collections.unmodifiableSet(trustedMembers);
    }

    public Map<ClaimFlag, Boolean> flags() {
        return Collections.unmodifiableMap(flags);
    }

    public boolean hasFlag(ClaimFlag flag) {
        return flags.getOrDefault(flag, false);
    }

    public boolean isTrusted(UUID playerId) {
        return owner.equals(playerId) || trustedMembers.contains(playerId);
    }

    public boolean addTrusted(UUID playerId) {
        return trustedMembers.add(playerId);
    }

    public boolean removeTrusted(UUID playerId) {
        return trustedMembers.remove(playerId);
    }

    public void setFlag(ClaimFlag flag, boolean value) {
        flags.put(flag, value);
    }

    public long createdTimestamp() {
        return createdTimestamp;
    }

    public long lastOwnerLogin() {
        return lastOwnerLogin;
    }

    public void setLastOwnerLogin(long lastOwnerLogin) {
        this.lastOwnerLogin = lastOwnerLogin;
    }

    public ChunkClaim copy() {
        return new ChunkClaim(key, owner, trustedMembers, flags, createdTimestamp, lastOwnerLogin);
    }
}
