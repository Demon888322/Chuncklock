package com.chunklock.visual;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.model.ChunkKey;
import com.chunklock.service.ClaimService;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimVisualizer {
    private static final Particle CLAIM_PARTICLE = claimParticle();
    private final ChunkLockPlugin plugin;
    private final ClaimService claims;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public ClaimVisualizer(ChunkLockPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    public void start() {
        long interval = Math.max(5L, plugin.getConfig().getLong("visualizer.interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderEnabled, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        enabledPlayers.clear();
    }

    public void toggle(Player player) {
        UUID playerId = player.getUniqueId();
        if (enabledPlayers.remove(playerId)) {
            plugin.messages().send(player, "visualization-off");
            return;
        }
        enabledPlayers.add(playerId);
        plugin.messages().send(player, "visualization-on");
        show(player);
    }

    public void onChunkEnter(Player player, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(player.getWorld().getUID(), chunkX, chunkZ);
        if (claims.get(key) != null) {
            show(player, chunkX, chunkZ);
        }
    }

    private void renderEnabled() {
        if (enabledPlayers.isEmpty()) {
            return;
        }
        for (UUID playerId : enabledPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                enabledPlayers.remove(playerId);
                continue;
            }
            show(player);
        }
    }

    private void show(Player player) {
        show(player, player.getLocation().getChunk().getX(), player.getLocation().getChunk().getZ());
    }

    private void show(Player player, int chunkX, int chunkZ) {
        World world = player.getWorld();
        double baseX = chunkX << 4;
        double baseZ = chunkZ << 4;
        double y = Math.min(world.getMaxHeight() - 1, player.getLocation().getBlockY() + 1.0);
        double spacing = Math.max(1.0, plugin.getConfig().getDouble("visualizer.spacing", 2.0));
        for (double offset = 0.5; offset < 16.0; offset += spacing) {
            particle(player, world, baseX + offset, y, baseZ + 0.5);
            particle(player, world, baseX + offset, y, baseZ + 15.5);
            particle(player, world, baseX + 0.5, y, baseZ + offset);
            particle(player, world, baseX + 15.5, y, baseZ + offset);
        }
    }

    private static void particle(Player player, World world, double x, double y, double z) {
        world.spawnParticle(CLAIM_PARTICLE, x, y, z, 1, 0, 0, 0, 0, null, true);
    }

    private static Particle claimParticle() {
        try {
            return Particle.valueOf("HAPPY_VILLAGER");
        } catch (IllegalArgumentException exception) {
            try {
                return Particle.valueOf("VILLAGER_HAPPY");
            } catch (IllegalArgumentException unsupported) {
                unsupported.addSuppressed(exception);
                throw new IllegalStateException("Paper API does not provide a supported claim particle.", unsupported);
            }
        }
    }
}
