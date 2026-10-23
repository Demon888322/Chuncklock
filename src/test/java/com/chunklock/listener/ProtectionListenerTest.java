package com.chunklock.listener;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import com.chunklock.model.ProgressTask;
import com.chunklock.service.ClaimService;
import com.chunklock.service.ProgressionService;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectionListenerTest {
    private ChunkLockPlugin plugin;
    private ClaimService claims;
    private ProgressionService progression;
    private ProtectionListener listener;
    private UUID worldId;
    private World world;
    private UUID ownerId;
    private ChunkClaim claim;

    @BeforeAll
    static void initializeBukkitServer() {
        Server server = mock(Server.class);
        when(server.getLogger()).thenReturn(Logger.getLogger("ProtectionListenerTest"));
        when(server.getName()).thenReturn("Test Paper");
        when(server.getVersion()).thenReturn("Test Paper");
        when(server.getBukkitVersion()).thenReturn("1.20.4-R0.1-SNAPSHOT");
        when(server.getMinecraftVersion()).thenReturn("1.20.4");
        when(server.getBukkitVersion()).thenReturn("1.20.4-R0.1-SNAPSHOT");
        when(server.getTag(anyString(), any(NamespacedKey.class), any(Class.class)))
                .thenAnswer(ignored -> mock(Tag.class));
        try {
            java.lang.reflect.Field instance = Bukkit.class.getDeclaredField("server");
            instance.setAccessible(true);
            instance.set(null, server);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @BeforeEach
    void setUp() {
        plugin = mock(ChunkLockPlugin.class);
        claims = mock(ClaimService.class);
        progression = mock(ProgressionService.class);
        world = mock(World.class);
        worldId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        when(plugin.claims()).thenReturn(claims);
        when(plugin.progression()).thenReturn(progression);
        when(world.getUID()).thenReturn(worldId);
        claim = ChunkClaim.create(new ChunkKey(worldId, 0, 0), ownerId);
        listener = new ProtectionListener(plugin);
    }

    @Test
    void cancelsBlockBreakUntilClaimDataIsReady() {
        when(claims.isReady()).thenReturn(false);
        BlockBreakEvent event = blockBreakEvent(UUID.randomUUID(), -1, 2);

        listener.onBlockBreak(event);

        assertTrue(event.isCancelled());
        verify(claims, never()).get(any());
    }

    @Test
    void countsSurvivalBlockBreaksButNotProtectedBreakAttempts() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(any())).thenReturn(null);
        Player player = player(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        BlockBreakEvent event = new BlockBreakEvent(block(1, 1), player);

        listener.onBlockBreak(event);

        verify(progression).record(player.getUniqueId(), ProgressTask.BLOCKS, 1);
        assertFalse(event.isCancelled());
    }

    @Test
    void permitsUnclaimedAndTrustedBlockBreaksButDeniesOtherPlayers() {
        UUID ownerId = UUID.randomUUID();
        UUID trustedId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(claims.isReady()).thenReturn(true);
        ChunkKey key = new ChunkKey(worldId, -1, 2);
        ChunkClaim claim = ChunkClaim.create(key, ownerId);
        when(claims.get(key)).thenReturn(null, claim, claim);

        BlockBreakEvent unclaimed = blockBreakEvent(strangerId, -1, 2);
        BlockBreakEvent stranger = blockBreakEvent(strangerId, -1, 2);
        BlockBreakEvent trusted = blockBreakEvent(trustedId, -1, 2);
        claim.addTrusted(trustedId);

        listener.onBlockBreak(unclaimed);
        listener.onBlockBreak(stranger);
        listener.onBlockBreak(trusted);

        assertFalse(unclaimed.isCancelled());
        assertTrue(stranger.isCancelled());
        assertFalse(trusted.isCancelled());
    }

    @Test
    void explosionProtectionRemovesOnlyBlocksInsideProtectedClaims() {
        when(claims.isReady()).thenReturn(true);
        ChunkKey protectedKey = new ChunkKey(worldId, 0, 0);
        ChunkKey openKey = new ChunkKey(worldId, 1, 0);
        when(claims.get(protectedKey)).thenReturn(
                ChunkClaim.create(protectedKey, UUID.randomUUID()));
        when(claims.get(openKey)).thenReturn(null);
        Block protectedBlock = block(0, 0);
        Block openBlock = block(16, 0);
        List<Block> blocks = new ArrayList<>(List.of(protectedBlock, openBlock));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(blocks);

        listener.onEntityExplode(event);

        assertEquals(List.of(openBlock), blocks);
    }

    @Test
    void allowsExplosionsWhenClaimFlagIsEnabled() {
        when(claims.isReady()).thenReturn(true);
        ChunkKey key = new ChunkKey(worldId, 0, 0);
        ChunkClaim claim = ChunkClaim.create(key, UUID.randomUUID());
        claim.setFlag(ClaimFlag.EXPLOSIONS, true);
        when(claims.get(key)).thenReturn(claim);
        Block block = block(0, 0);
        List<Block> blocks = new ArrayList<>(List.of(block));
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(blocks);

        listener.onEntityExplode(event);

        assertEquals(List.of(block), blocks);
    }

    @Test
    void protectsBlockPlacementAndAllowsTrustedMembers() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(claim.key())).thenReturn(claim);
        UUID trustedId = UUID.randomUUID();
        claim.addTrusted(trustedId);
        Block block = block(1, 1);
        BlockPlaceEvent strangerEvent = blockPlaceEvent(block, UUID.randomUUID());
        BlockPlaceEvent trustedEvent = blockPlaceEvent(block, trustedId);

        listener.onBlockPlace(strangerEvent);
        listener.onBlockPlace(trustedEvent);

        assertTrue(strangerEvent.isCancelled());
        assertFalse(trustedEvent.isCancelled());
    }

    @Test
    void containerAccessFlagControlsInteractionsAndInventoryOpening() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(claim.key())).thenReturn(claim);
        Player player = player(UUID.randomUUID());
        Block containerBlock = block(1, 1);
        Container container = mock(Container.class);
        when(containerBlock.getState()).thenReturn(container);

        PlayerInteractEvent deniedInteraction = interactEvent(player, containerBlock);
        listener.onInteract(deniedInteraction);
        assertTrue(deniedInteraction.isCancelled());

        claim.setFlag(ClaimFlag.CONTAINER_ACCESS, true);
        PlayerInteractEvent allowedInteraction = interactEvent(player, containerBlock);
        listener.onInteract(allowedInteraction);
        assertFalse(allowedInteraction.isCancelled());

        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(location(1, 1));
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryOpenEvent openEvent = new InventoryOpenEvent(view);
        listener.onInventoryOpen(openEvent);
        assertFalse(openEvent.isCancelled());

        claim.setFlag(ClaimFlag.CONTAINER_ACCESS, false);
        InventoryOpenEvent deniedOpenEvent = new InventoryOpenEvent(view);
        listener.onInventoryOpen(deniedOpenEvent);
        assertTrue(deniedOpenEvent.isCancelled());
    }

    @Test
    void redstoneFlagControlsSwitchInteractions() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(claim.key())).thenReturn(claim);
        Player player = player(UUID.randomUUID());
        Block switchBlock = block(1, 1);
        Switch switchData = mock(Switch.class);
        when(switchBlock.getBlockData()).thenReturn(switchData);

        PlayerInteractEvent denied = interactEvent(player, switchBlock);
        listener.onInteract(denied);
        assertTrue(denied.isCancelled());

        claim.setFlag(ClaimFlag.REDSTONE, true);
        PlayerInteractEvent allowed = interactEvent(player, switchBlock);
        listener.onInteract(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void pvpFlagControlsPlayerCombatInsideClaims() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(claim.key())).thenReturn(claim);
        Player attacker = player(UUID.randomUUID());
        Player target = player(UUID.randomUUID());
        when(target.getLocation()).thenReturn(location(1, 1));

        EntityDamageByEntityEvent denied = damageEvent(attacker, target);
        listener.onEntityDamage(denied);
        verify(denied).setCancelled(true);

        claim.setFlag(ClaimFlag.PVP, true);
        EntityDamageByEntityEvent allowed = damageEvent(attacker, target);
        listener.onEntityDamage(allowed);
        verify(allowed, never()).setCancelled(true);

        claim.setFlag(ClaimFlag.PVP, false);
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent projectileAttack = damageEvent(projectile, target);
        listener.onEntityDamage(projectileAttack);
        verify(projectileAttack).setCancelled(true);
    }

    @Test
    void naturalMobSpawningIsControlledByItsClaimFlag() {
        when(claims.isReady()).thenReturn(true);
        when(claims.get(claim.key())).thenReturn(claim);
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getLocation()).thenReturn(location(1, 1));
        CreatureSpawnEvent naturalSpawn = new CreatureSpawnEvent(mob, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onCreatureSpawn(naturalSpawn);
        assertTrue(naturalSpawn.isCancelled());

        claim.setFlag(ClaimFlag.MOB_SPAWNING, true);
        CreatureSpawnEvent allowedSpawn = new CreatureSpawnEvent(mob, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onCreatureSpawn(allowedSpawn);
        assertFalse(allowedSpawn.isCancelled());

        claim.setFlag(ClaimFlag.MOB_SPAWNING, false);
        CreatureSpawnEvent pluginSpawn = new CreatureSpawnEvent(mob, CreatureSpawnEvent.SpawnReason.CUSTOM);
        listener.onCreatureSpawn(pluginSpawn);
        assertFalse(pluginSpawn.isCancelled());
    }

    @Test
    void countsHostileMobKillsForTheKiller() {
        Monster monster = mock(Monster.class);
        Player killer = player(UUID.randomUUID());
        when(monster.getKiller()).thenReturn(killer);
        when(killer.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(monster);

        listener.onEntityDeath(event);

        verify(progression).record(killer.getUniqueId(), ProgressTask.HOSTILE_MOBS, 1);
    }

    @Test
    void hopperTransfersCannotMoveItemsIntoAnotherOwnersProtectedClaim() {
        when(claims.isReady()).thenReturn(true);
        ChunkClaim destinationClaim = claim;
        ChunkKey destinationKey = destinationClaim.key();
        ChunkKey sourceKey = new ChunkKey(worldId, 1, 0);
        when(claims.get(destinationKey)).thenReturn(destinationClaim);
        when(claims.get(sourceKey)).thenReturn(null);
        Inventory source = mock(Inventory.class);
        Inventory destination = mock(Inventory.class);
        when(source.getLocation()).thenReturn(location(16, 0));
        when(destination.getLocation()).thenReturn(location(0, 0));
        ItemStack item = mock(ItemStack.class);
        InventoryMoveItemEvent denied = new InventoryMoveItemEvent(source, item, destination, true);

        listener.onInventoryMove(denied);

        assertTrue(denied.isCancelled());
        when(claims.get(sourceKey)).thenReturn(
                ChunkClaim.create(sourceKey, destinationClaim.owner()));
        InventoryMoveItemEvent sameOwner = new InventoryMoveItemEvent(source, item, destination, true);
        listener.onInventoryMove(sameOwner);
        assertFalse(sameOwner.isCancelled());
    }

    @Test
    void openingAContainerIsBlockedWhileClaimDataLoads() {
        when(claims.isReady()).thenReturn(false);
        Player player = player(UUID.randomUUID());
        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(location(1, 1));
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryOpenEvent event = new InventoryOpenEvent(view);

        listener.onInventoryOpen(event);

        assertTrue(event.isCancelled());
    }

    private BlockBreakEvent blockBreakEvent(UUID playerId, int x, int z) {
        Player player = player(playerId);
        return new BlockBreakEvent(block(x << 4, z << 4), player);
    }

    private BlockPlaceEvent blockPlaceEvent(Block block, UUID playerId) {
        return new BlockPlaceEvent(block, block.getState(), block, null, player(playerId), true);
    }

    private PlayerInteractEvent interactEvent(Player player, Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH);
    }

    private EntityDamageByEntityEvent damageEvent(Entity attacker, Entity target) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(target);
        return event;
    }

    private Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    private Location location(int x, int z) {
        return new Location(world, x, 0, z);
    }

    private Block block(int x, int z) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        return block;
    }
}
