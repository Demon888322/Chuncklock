package com.chunklock.listener;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class ProtectionListener implements Listener {
    private final ChunkLockPlugin plugin;

    public ProtectionListener(ChunkLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        denyUnlessTrusted(event, key(event.getBlock()), event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        denyUnlessTrusted(event, key(event.getBlock()), event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isProtectedInteractable(block)) {
            return;
        }
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        ChunkClaim claim = plugin.claims().get(key(block));
        if (claim == null || claim.isTrusted(event.getPlayer().getUniqueId())) {
            return;
        }
        if (isContainer(block) && claim.hasFlag(ClaimFlag.CONTAINER_ACCESS)) {
            return;
        }
        if (isRedstoneControl(block) && claim.hasFlag(ClaimFlag.REDSTONE)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        ChunkClaim claim = plugin.claims().get(key(location));
        if (claim != null && !claim.isTrusted(player.getUniqueId())
                && !claim.hasFlag(ClaimFlag.CONTAINER_ACCESS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        boolean playerVsPlayer = target instanceof Player && playerAttacker(event.getDamager()) != null;
        boolean protectedEntity = target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Animals;
        if (!playerVsPlayer && !protectedEntity) {
            return;
        }
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        ChunkClaim claim = plugin.claims().get(key(target.getLocation()));
        if (claim == null || claim.hasFlag(ClaimFlag.PVP)) {
            return;
        }
        Player attacker = playerAttacker(event.getDamager());
        if (attacker == null || !claim.isTrusted(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> blocksExplosion(block));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> blocksExplosion(block));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        Inventory source = event.getSource();
        Location destinationLocation = destination.getLocation();
        Location sourceLocation = source.getLocation();
        if (destinationLocation == null || destinationLocation.getWorld() == null
                || sourceLocation == null || sourceLocation.getWorld() == null) {
            return;
        }
        ChunkKey destinationKey = key(destinationLocation);
        if (!plugin.claims().isReady()) {
            event.setCancelled(true);
            return;
        }
        ChunkClaim destinationClaim = plugin.claims().get(destinationKey);
        if (destinationClaim == null || destinationClaim.hasFlag(ClaimFlag.CONTAINER_ACCESS)) {
            return;
        }
        ChunkKey sourceKey = key(sourceLocation);
        if (destinationKey.equals(sourceKey)) {
            return;
        }
        ChunkClaim sourceClaim = plugin.claims().get(sourceKey);
        if (sourceClaim == null || !sourceClaim.owner().equals(destinationClaim.owner())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.claims().isReady()) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
            return;
        }
        ChunkClaim claim = plugin.claims().get(key(event.getLocation()));
        if (claim != null && !claim.hasFlag(ClaimFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || event.getFrom().getWorld() != to.getWorld()
                || (event.getFrom().getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (event.getFrom().getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        onChunkEntered(event.getPlayer(), to.getBlockX() >> 4, to.getBlockZ() >> 4);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        onChunkEntered(player, player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
    }

    private void onChunkEntered(Player player, int x, int z) {
        if (!plugin.claims().isReady()) {
            return;
        }
        ChunkClaim claim = plugin.claims().get(new ChunkKey(player.getWorld().getUID(), x, z));
        if (claim == null) {
            return;
        }
        plugin.visualizer().onChunkEnter(player, x, z);
        if (claim.owner().equals(player.getUniqueId())
                && System.currentTimeMillis() - claim.lastOwnerLogin() > 300_000L) {
            plugin.reportSaveResult(player, plugin.claims().recordOwnerLogin(claim));
        }
    }

    private boolean blocksExplosion(Block block) {
        ChunkClaim claim = plugin.claims().get(key(block));
        return claim != null && !claim.hasFlag(ClaimFlag.EXPLOSIONS);
    }

    private void denyUnlessTrusted(Cancellable event, ChunkKey key, UUID playerId) {
        ChunkClaim claim = plugin.claims().get(key);
        if (claim != null && !claim.isTrusted(playerId)) {
            event.setCancelled(true);
        }
    }

    private static Player playerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    private static boolean isProtectedInteractable(Block block) {
        return isContainer(block) || isRedstoneControl(block)
                || Tag.DOORS.isTagged(block.getType())
                || Tag.TRAPDOORS.isTagged(block.getType())
                || Tag.FENCE_GATES.isTagged(block.getType());
    }

    private static boolean isContainer(Block block) {
        BlockState state = block.getState();
        return state instanceof Container;
    }

    private static boolean isRedstoneControl(Block block) {
        return block.getBlockData() instanceof Switch
                || Tag.BUTTONS.isTagged(block.getType())
                || Tag.PRESSURE_PLATES.isTagged(block.getType());
    }

    private static ChunkKey key(Block block) {
        return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private static ChunkKey key(Location location) {
        return new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
