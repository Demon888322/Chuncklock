package com.chunklock.command;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChunkCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS =
            List.of("claim", "unclaim", "trust", "untrust", "flag", "info", "visualize", "admin");
    private final ChunkLockPlugin plugin;

    public ChunkCommand(ChunkLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "usage");
            return true;
        }
        if (args[0].equalsIgnoreCase("admin")) {
            handleAdmin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!plugin.claims().isReady()) {
            plugin.messages().send(player, "database-loading");
            return true;
        }

        ChunkKey key = currentKey(player);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(player, key);
            case "unclaim" -> unclaim(player, key);
            case "trust" -> trust(player, key, args, true);
            case "untrust" -> trust(player, key, args, false);
            case "flag" -> flag(player, key, args);
            case "info" -> info(player, key);
            case "visualize" -> plugin.visualizer().toggle(player);
            default -> plugin.messages().send(player, "usage");
        }
        return true;
    }

    private void claim(Player player, ChunkKey key) {
        if (!player.hasPermission("chunklock.claim")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.claims().get(key) != null) {
            plugin.messages().send(player, "already-claimed");
            return;
        }
        int limit = getLimit(player);
        if (plugin.claims().countOwned(player.getUniqueId()) >= limit) {
            plugin.messages().send(player, "claim-limit", Map.of("limit", Integer.toString(limit)));
            return;
        }
        ChunkClaim claim = ChunkClaim.create(key, player.getUniqueId());
        plugin.reportSaveResult(player, plugin.claims().create(claim));
        plugin.messages().send(player, "claimed", Map.of("x", Integer.toString(key.x()), "z", Integer.toString(key.z())));
    }

    private void unclaim(Player player, ChunkKey key) {
        if (!player.hasPermission("chunklock.claim")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        ChunkClaim claim = plugin.claims().get(key);
        if (claim == null) {
            plugin.messages().send(player, "not-claimed");
            return;
        }
        if (!plugin.claims().owns(claim, player.getUniqueId())) {
            plugin.messages().send(player, "not-owner");
            return;
        }
        plugin.reportSaveResult(player, plugin.claims().unclaim(key));
        plugin.messages().send(player, "unclaimed");
    }

    private void trust(Player player, ChunkKey key, String[] args, boolean add) {
        if (!player.hasPermission("chunklock.claim")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "usage");
            return;
        }
        ChunkClaim claim = plugin.claims().get(key);
        if (claim == null) {
            plugin.messages().send(player, "not-claimed");
            return;
        }
        if (!plugin.claims().owns(claim, player.getUniqueId())) {
            plugin.messages().send(player, "not-owner");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(player, "player-not-online");
            return;
        }
        boolean changed = add ? claim.addTrusted(target.getUniqueId()) : claim.removeTrusted(target.getUniqueId());
        if (!changed) {
            plugin.messages().send(player, add ? "already-trusted" : "not-trusted");
            return;
        }
        plugin.reportSaveResult(player, plugin.claims().save(claim));
        plugin.messages().send(player, add ? "trusted" : "untrusted", Map.of("player", target.getName()));
    }

    private void flag(Player player, ChunkKey key, String[] args) {
        if (!player.hasPermission("chunklock.flag")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(player, "usage");
            return;
        }
        ChunkClaim claim = plugin.claims().get(key);
        if (claim == null) {
            plugin.messages().send(player, "not-claimed");
            return;
        }
        if (!plugin.claims().owns(claim, player.getUniqueId())) {
            plugin.messages().send(player, "not-owner");
            return;
        }
        ClaimFlag flag = ClaimFlag.fromCommand(args[1]);
        if (flag == null) {
            plugin.messages().send(player, "invalid-flag");
            return;
        }
        if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
            plugin.messages().send(player, "invalid-boolean");
            return;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        claim.setFlag(flag, value);
        plugin.reportSaveResult(player, plugin.claims().save(claim));
        plugin.messages().send(player, "flag-set",
                Map.of("flag", flag.commandName(), "value", Boolean.toString(value)));
    }

    private void info(Player player, ChunkKey key) {
        ChunkClaim claim = plugin.claims().get(key);
        if (claim == null) {
            plugin.messages().send(player, "not-claimed");
            return;
        }
        plugin.messages().send(player, "info-header");
        plugin.messages().send(player, "info-owner", Map.of("owner", displayName(claim.owner())));
        String trusted = claim.trustedMembers().stream()
                .map(this::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        plugin.messages().send(player, "info-trusted", Map.of("members", trusted.isEmpty() ? "none" : trusted));
        String flags = claim.flags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().commandName() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        plugin.messages().send(player, "info-flags", Map.of("flags", flags));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunklock.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!plugin.claims().isReady()) {
            plugin.messages().send(player, "database-loading");
            return;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("unclaim")) {
            plugin.messages().send(player, "usage");
            return;
        }
        ChunkKey key = currentKey(player);
        if (plugin.claims().get(key) == null) {
            plugin.messages().send(player, "not-claimed");
            return;
        }
        plugin.reportSaveResult(player, plugin.claims().unclaim(key));
        plugin.messages().send(player, "admin-unclaimed");
    }

    private int getLimit(Player player) {
        int defaultLimit = Math.max(0, plugin.getConfig().getInt("claims.default-limit", 8));
        if (!player.isPermissionSet("chunklock.claim")) {
            return defaultLimit;
        }
        if (plugin.getConfig().isConfigurationSection("claims.permission-limits")) {
            Set<String> keys = plugin.getConfig().getConfigurationSection("claims.permission-limits").getKeys(false);
            List<Integer> limits = new ArrayList<>();
            for (String key : keys) {
                try {
                    limits.add(Integer.parseInt(key));
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("Ignoring invalid claim limit key: " + key);
                }
            }
            limits.sort(Comparator.reverseOrder());
            for (int configuredLimit : limits) {
                String permission = plugin.getConfig().getString("claims.permission-limits." + configuredLimit);
                if (permission != null && player.hasPermission(permission)) {
                    return Math.max(0, configuredLimit);
                }
            }
        }
        return defaultLimit;
    }

    private String displayName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        return online == null ? playerId.toString().substring(0, 8) : online.getName();
    }

    private static ChunkKey currentKey(Player player) {
        return new ChunkKey(player.getWorld().getUID(), player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.regionMatches(true, 0, args[1], 0, args[1].length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("flag")) {
            return partial(List.of("pvp", "explosions", "chest-access", "redstone", "mob-spawning"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            return partial(List.of("true", "false"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return partial(List.of("unclaim"), args[1]);
        }
        return List.of();
    }

    private static List<String> partial(List<String> options, String prefix) {
        return options.stream()
                .filter(option -> option.regionMatches(true, 0, prefix, 0, prefix.length()))
                .toList();
    }
}
