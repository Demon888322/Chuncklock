package com.chunklock.storage;

import com.chunklock.model.ChunkClaim;
import com.chunklock.model.PlayerProgress;

import java.util.List;

public record StoredData(List<ChunkClaim> claims, List<PlayerProgress> playerProgress) {
}
