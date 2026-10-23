package com.chunklock.model;

import java.util.UUID;

public final class PlayerProgress {
    private final UUID playerId;
    private int unlockedClaims;
    private long taskProgress;
    private long revision;
    private long persistedRevision;

    public PlayerProgress(UUID playerId, int unlockedClaims, long taskProgress) {
        this.playerId = playerId;
        this.unlockedClaims = Math.max(0, unlockedClaims);
        this.taskProgress = Math.max(0L, taskProgress);
    }

    public synchronized UUID playerId() {
        return playerId;
    }

    public synchronized int unlockedClaims() {
        return unlockedClaims;
    }

    public synchronized long taskProgress() {
        return taskProgress;
    }

    public synchronized void addTaskProgress(long amount, long maximum) {
        if (amount <= 0 || taskProgress >= maximum) {
            return;
        }
        taskProgress = amount >= maximum - taskProgress ? maximum : taskProgress + amount;
        revision++;
    }

    public synchronized void unlockClaim() {
        unlockedClaims++;
        taskProgress = 0;
        revision++;
    }

    public synchronized boolean isDirty() {
        return revision != persistedRevision;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void markPersisted(long savedRevision) {
        if (revision == savedRevision) {
            persistedRevision = savedRevision;
        }
    }

    public synchronized PlayerProgress snapshot() {
        PlayerProgress snapshot = new PlayerProgress(playerId, unlockedClaims, taskProgress);
        snapshot.revision = revision;
        snapshot.persistedRevision = revision;
        return snapshot;
    }
}
