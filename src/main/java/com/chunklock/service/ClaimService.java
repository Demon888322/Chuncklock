package com.chunklock.service;

import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.storage.ClaimRepository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClaimService {
    private final ClaimRepository repository;
    private final Map<ChunkKey, ChunkClaim> claims = new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public ClaimService(ClaimRepository repository) {
        this.repository = repository;
    }

    public void initialize(Collection<ChunkClaim> loadedClaims) {
        for (ChunkClaim claim : loadedClaims) {
            claims.put(claim.key(), claim);
        }
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }

    public ChunkClaim get(ChunkKey key) {
        return claims.get(key);
    }

    public boolean owns(ChunkClaim claim, UUID playerId) {
        return claim != null && claim.owner().equals(playerId);
    }

    public int countOwned(UUID playerId) {
        int count = 0;
        for (ChunkClaim claim : claims.values()) {
            if (claim.owner().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    public CompletableFuture<Void> create(ChunkClaim claim) {
        claims.put(claim.key(), claim);
        return repository.save(claim);
    }

    public CompletableFuture<Void> save(ChunkClaim claim) {
        return repository.save(claim);
    }

    public CompletableFuture<Void> unclaim(ChunkKey key) {
        claims.remove(key);
        return repository.delete(key);
    }

    public CompletableFuture<Void> recordOwnerLogin(ChunkClaim claim) {
        claim.setLastOwnerLogin(System.currentTimeMillis());
        return repository.save(claim);
    }
}
