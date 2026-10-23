package com.chunklock.storage;

import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import com.chunklock.model.PlayerProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsClaimsMembersFlagsAndLoginTime() throws Exception {
        JavaPlugin plugin = plugin("claims.db");
        ClaimRepository repository = new ClaimRepository(plugin);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ChunkKey key = new ChunkKey(UUID.randomUUID(), -7, 11);
        try {
            assertTrue(repository.initializeAndLoad().get(20, TimeUnit.SECONDS).claims().isEmpty());
            ChunkClaim claim = new ChunkClaim(key, owner, java.util.Set.of(member),
                    java.util.Map.of(ClaimFlag.PVP, true, ClaimFlag.CONTAINER_ACCESS, true), 123L, 456L);

            repository.save(claim).get(20, TimeUnit.SECONDS);
            PlayerProgress progress = new PlayerProgress(owner, 3, 19L);
            repository.saveProgress(progress).get(20, TimeUnit.SECONDS);
        } finally {
            repository.close();
        }

        ClaimRepository reloadedRepository = new ClaimRepository(plugin);
        try {
            var storedData = reloadedRepository.initializeAndLoad().get(20, TimeUnit.SECONDS);
            List<ChunkClaim> loadedClaims = storedData.claims();
            assertEquals(1, loadedClaims.size());
            ChunkClaim loaded = loadedClaims.get(0);
            assertEquals(key, loaded.key());
            assertEquals(owner, loaded.owner());
            assertTrue(loaded.isTrusted(member));
            assertTrue(loaded.hasFlag(ClaimFlag.PVP));
            assertTrue(loaded.hasFlag(ClaimFlag.CONTAINER_ACCESS));
            assertFalse(loaded.hasFlag(ClaimFlag.REDSTONE));
            assertEquals(123L, loaded.createdTimestamp());
            assertEquals(456L, loaded.lastOwnerLogin());
            assertEquals(1, storedData.playerProgress().size());
            assertEquals(owner, storedData.playerProgress().get(0).playerId());
            assertEquals(3, storedData.playerProgress().get(0).unlockedClaims());
            assertEquals(19L, storedData.playerProgress().get(0).taskProgress());

            reloadedRepository.delete(key).get(20, TimeUnit.SECONDS);
        } finally {
            reloadedRepository.close();
        }

        ClaimRepository verificationRepository = new ClaimRepository(plugin);
        try {
            var storedData = verificationRepository.initializeAndLoad().get(20, TimeUnit.SECONDS);
            assertTrue(storedData.claims().isEmpty());
            assertEquals(1, storedData.playerProgress().size());
        } finally {
            verificationRepository.close();
        }
    }

    @Test
    void rejectsUnsupportedStorageType() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "unknown");
        JavaPlugin plugin = plugin(config);
        ClaimRepository repository = new ClaimRepository(plugin);
        try {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> repository.initializeAndLoad().get(20, TimeUnit.SECONDS));
        } finally {
            repository.close();
        }
    }

    private JavaPlugin plugin(String databaseName) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", databaseName);
        return plugin(config);
    }

    private JavaPlugin plugin(YamlConfiguration config) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("ClaimRepositoryTest"));
        return plugin;
    }
}
