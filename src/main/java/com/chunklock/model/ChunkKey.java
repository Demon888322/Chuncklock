package com.chunklock.model;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
}
