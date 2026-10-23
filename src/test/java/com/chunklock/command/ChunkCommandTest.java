package com.chunklock.command;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import com.chunklock.service.ClaimService;
import com.chunklock.util.Messages;
import com.chunklock.visual.ClaimVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkCommandTest {
    private ChunkLockPlugin plugin;
    private ClaimService claims;
    private Messages messages;
    private ClaimVisualizer visualizer;
    private ChunkCommand command;
    private Player player;
    private ChunkKey key;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        plugin = mock(ChunkLockPlugin.class);
        claims = mock(ClaimService.class);
        messages = mock(Messages.class);
        visualizer = mock(ClaimVisualizer.class);
        player = mock(Player.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Chunk chunk = mock(Chunk.class);
        bukkitCommand = mock(Command.class);
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        key = new ChunkKey(worldId, -2, 3);
        YamlConfiguration config = new YamlConfiguration();
        config.set("claims.default-limit", 8);

        when(plugin.claims()).thenReturn(claims);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.visualizer()).thenReturn(visualizer);
        when(plugin.getConfig()).thenReturn(config);
        when(claims.isReady()).thenReturn(true);
        when(claims.owns(any(), any())).thenAnswer(invocation -> {
            ChunkClaim claim = invocation.getArgument(0);
            UUID ownerId = invocation.getArgument(1);
            return claim != null && claim.owner().equals(ownerId);
        });
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(world.getUID()).thenReturn(worldId);
        when(location.getChunk()).thenReturn(chunk);
        when(chunk.getX()).thenReturn(-2);
        when(chunk.getZ()).thenReturn(3);
        command = new ChunkCommand(plugin);
    }

    @Test
    void claimsCurrentChunkWhenLimitAllowsIt() {
        when(claims.get(key)).thenReturn(null);
        when(claims.countOwned(any())).thenReturn(0);
        when(claims.create(any())).thenReturn(CompletableFuture.completedFuture(null));

        assertTrue(command.onCommand(player, bukkitCommand, "chunk", new String[]{"claim"}));

        verify(claims).create(argThat(claim -> claim.key().equals(key)
                && claim.owner().equals(player.getUniqueId())));
        verify(messages).send(player, "claimed", java.util.Map.of("x", "-2", "z", "3"));
    }

    @Test
    void rejectsClaimWhenThePlayerHasReachedTheirLimit() {
        when(claims.get(key)).thenReturn(null);
        when(claims.countOwned(any())).thenReturn(8);

        command.onCommand(player, bukkitCommand, "chunk", new String[]{"claim"});

        verify(claims, never()).create(any());
        verify(messages).send(player, "claim-limit", java.util.Map.of("limit", "8"));
    }

    @Test
    void onlyAllowsOwnersToUnclaimTheirChunk() {
        ChunkClaim claim = ChunkClaim.create(key, UUID.randomUUID());
        when(claims.get(key)).thenReturn(claim);

        command.onCommand(player, bukkitCommand, "chunk", new String[]{"unclaim"});

        verify(claims, never()).unclaim(any());
        verify(messages).send(player, "not-owner");
    }

    @Test
    void setsFlagsUsingAcceptedAliasesAndRejectsInvalidValues() {
        ChunkClaim claim = ChunkClaim.create(key, player.getUniqueId());
        when(claims.get(key)).thenReturn(claim);
        when(claims.save(claim)).thenReturn(CompletableFuture.completedFuture(null));

        command.onCommand(player, bukkitCommand, "chunk", new String[]{"flag", "containers", "true"});

        verify(messages).send(player, "flag-set", java.util.Map.of("flag", "chest-access", "value", "true"));
        assertTrue(claim.hasFlag(ClaimFlag.CONTAINER_ACCESS));
        verify(claims).save(claim);

        clearInvocations(messages, claims);
        command.onCommand(player, bukkitCommand, "chunk", new String[]{"flag", "pvp", "yes"});

        assertFalse(claim.hasFlag(ClaimFlag.PVP));
        verify(claims, never()).save(any());
        verify(messages).send(player, "invalid-boolean");
    }

    @Test
    void trustsAnOnlinePlayerOnlyOnTheOwnerClaim() {
        ChunkClaim claim = ChunkClaim.create(key, player.getUniqueId());
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Trusted");
        when(claims.get(key)).thenReturn(claim);
        when(claims.save(claim)).thenReturn(CompletableFuture.completedFuture(null));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Trusted")).thenReturn(target);

            command.onCommand(player, bukkitCommand, "chunk", new String[]{"trust", "Trusted"});

            verify(messages).send(player, "trusted", java.util.Map.of("player", "Trusted"));
            assertTrue(claim.isTrusted(targetId));
            verify(claims).save(claim);
        }
    }

    @Test
    void adminCanUnclaimAndTabCompletionOffersAvailableCommands() {
        ChunkClaim claim = ChunkClaim.create(key, UUID.randomUUID());
        when(claims.get(key)).thenReturn(claim);
        when(claims.unclaim(key)).thenReturn(CompletableFuture.completedFuture(null));

        command.onCommand(player, bukkitCommand, "chunk", new String[]{"admin", "unclaim"});

        verify(claims).unclaim(key);
        verify(messages).send(player, "admin-unclaimed");
        assertEquals(List.of("claim"), command.onTabComplete(
                player, bukkitCommand, "chunk", new String[]{"cl"}));
        assertEquals(List.of("unclaim"), command.onTabComplete(
                player, bukkitCommand, "chunk", new String[]{"admin", "un"}));
    }

    @Test
    void visualizationCommandDelegatesToVisualizer() {
        command.onCommand(player, bukkitCommand, "chunk", new String[]{"visualize"});

        verify(visualizer).toggle(player);
    }

    @Test
    void infoCommandReportsOwnerMembersAndFlags() {
        ChunkClaim claim = ChunkClaim.create(key, UUID.randomUUID());
        UUID trustedId = UUID.randomUUID();
        claim.addTrusted(trustedId);
        claim.setFlag(ClaimFlag.PVP, true);
        when(claims.get(key)).thenReturn(claim);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(claim.owner())).thenReturn(null);
            bukkit.when(() -> Bukkit.getPlayer(trustedId)).thenReturn(null);

            command.onCommand(player, bukkitCommand, "chunk", new String[]{"info"});

            verify(messages).send(player, "info-header");
            verify(messages).send(player, "info-owner",
                    java.util.Map.of("owner", claim.owner().toString().substring(0, 8)));
            verify(messages).send(player, "info-trusted",
                    java.util.Map.of("members", trustedId.toString().substring(0, 8)));
            verify(messages).send(eq(player), eq("info-flags"), argThat(values ->
                    values.get("flags").contains("pvp=true")));
        }
    }
}
