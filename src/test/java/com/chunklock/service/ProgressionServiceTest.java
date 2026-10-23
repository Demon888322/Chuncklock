package com.chunklock.service;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.economy.CurrencyProvider;
import com.chunklock.model.PlayerProgress;
import com.chunklock.model.ProgressTask;
import com.chunklock.storage.ClaimRepository;
import com.chunklock.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProgressionServiceTest {
    private ChunkLockPlugin plugin;
    private ClaimRepository repository;
    private ProgressionService progression;
    private YamlConfiguration config;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        plugin = mock(ChunkLockPlugin.class);
        repository = mock(ClaimRepository.class);
        config = new YamlConfiguration();
        config.set("progression.enabled", true);
        config.set("progression.starting-claims", 16);
        config.set("progression.maximum-claims", 100);
        config.set("progression.tasks.enabled", true);
        config.set("progression.tasks.difficulty-multiplier", 1.5);
        config.set("progression.tasks.playtime.base-minutes", 15);
        config.set("progression.tasks.blocks.base-mined", 64);
        config.set("progression.tasks.hostile-mobs.base-defeated", 5);
        config.set("progression.economy.enabled", true);
        config.set("progression.economy.cost-per-claim", 5000.0);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ProgressionServiceTest"));
        when(repository.saveProgress(any(PlayerProgress.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        progression = new ProgressionService(plugin, repository);
        progression.initialize(List.of());
        playerId = UUID.randomUUID();
    }

    @Test
    void grantsSixteenConfigurableSlotsAndCapsPermissionLimits() {
        assertEquals(16, progression.claimLimit(playerId, 0));
        assertEquals(25, progression.claimLimit(playerId, 25));

        config.set("progression.starting-claims", 20);
        assertEquals(20, progression.claimLimit(playerId, 0));
    }

    @Test
    void tracksOnlyTheCurrentObjectiveAndCapsProgressAtItsTarget() {
        progression.record(playerId, ProgressTask.BLOCKS, 100);
        assertEquals(0, progression.taskProgress(playerId));

        progression.record(playerId, ProgressTask.PLAYTIME, 100);
        assertEquals(15, progression.taskProgress(playerId));
        assertEquals(15, progression.currentObjective(playerId).progress());
        assertEquals(ProgressionService.UnlockResult.SAVING, progression.unlockWithTask(player(playerId)));
        assertEquals(1, progression.unlockedClaims(playerId));
        assertEquals(0, progression.taskProgress(playerId));
        assertEquals(ProgressTask.BLOCKS, progression.currentObjective(playerId).task());
    }

    @Test
    void taskObjectivesBecomeHarderAfterEachFullCycle() {
        for (int index = 0; index < 3; index++) {
            ProgressionService.Objective objective = progression.currentObjective(playerId);
            progression.record(playerId, objective.task(), objective.target());
            assertEquals(ProgressionService.UnlockResult.SAVING,
                    progression.unlockWithTask(player(playerId)));
        }

        ProgressionService.Objective nextCycle = progression.currentObjective(playerId);
        assertEquals(ProgressTask.PLAYTIME, nextCycle.task());
        assertEquals(23, nextCycle.target());
    }

    @Test
    void unlocksWithCurrencyAndPersistsTheAdditionalSlot() {
        CurrencyProvider currency = mock(CurrencyProvider.class);
        Player player = player(playerId);
        progression.setCurrencyProvider(currency);
        when(currency.withdraw(player, 5000.0)).thenReturn(true);

        assertEquals(ProgressionService.UnlockResult.SAVING, progression.unlockWithMoney(player));

        verify(currency).withdraw(player, 5000.0);
        verify(repository).saveProgress(argThat(saved -> saved.unlockedClaims() == 1));
        assertEquals(17, progression.claimLimit(playerId, 0));
    }

    @Test
    void refundsCurrencyWhenTheClaimSlotCannotBePersisted() {
        CurrencyProvider currency = mock(CurrencyProvider.class);
        Messages messages = mock(Messages.class);
        Player player = player(playerId);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(player.isOnline()).thenReturn(true);
        when(plugin.messages()).thenReturn(messages);
        when(currency.withdraw(player, 5000.0)).thenReturn(true);
        when(repository.saveProgress(any(PlayerProgress.class))).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return mock(BukkitTask.class);
        });
        progression.setCurrencyProvider(currency);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertEquals(ProgressionService.UnlockResult.SAVING, progression.unlockWithMoney(player));
        }

        verify(currency).refund(player, 5000.0);
        verify(messages).send(player, "progression-save-failed");
        assertEquals(16, progression.claimLimit(playerId, 0));
    }

    @Test
    void moneyUnlockIsUnavailableWithoutVaultButTaskUnlockStillWorks() {
        assertFalse(progression.economyEnabled());
        assertEquals(ProgressionService.UnlockResult.ECONOMY_UNAVAILABLE,
                progression.unlockWithMoney(player(playerId)));

        progression.record(playerId, ProgressTask.PLAYTIME, 15);
        assertEquals(ProgressionService.UnlockResult.SAVING, progression.unlockWithTask(player(playerId)));
        assertEquals(17, progression.claimLimit(playerId, 0));
    }

    @Test
    void stopsProgressionAtConfiguredMaximum() {
        config.set("progression.maximum-claims", 16);
        CurrencyProvider currency = mock(CurrencyProvider.class);
        progression.setCurrencyProvider(currency);

        assertEquals(ProgressionService.UnlockResult.MAXIMUM_REACHED,
                progression.unlockWithMoney(player(playerId)));
        verifyNoInteractions(currency);
    }

    @Test
    void disablingProgressionRetainsLegacyDefaultAndPermissionLimits() {
        config.set("progression.enabled", false);
        config.set("claims.default-limit", 8);

        assertEquals(8, progression.claimLimit(playerId, 0));
        assertEquals(20, progression.claimLimit(playerId, 20));
        assertFalse(progression.tasksEnabled());
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }
}
