package com.chunklock.service;

import com.chunklock.ChunkLockPlugin;
import com.chunklock.economy.CurrencyProvider;
import com.chunklock.model.PlayerProgress;
import com.chunklock.model.ProgressTask;
import com.chunklock.storage.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionService {
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final List<ProgressTask> TASK_CYCLE = List.of(
            ProgressTask.PLAYTIME, ProgressTask.BLOCKS, ProgressTask.HOSTILE_MOBS);

    private final ChunkLockPlugin plugin;
    private final ClaimRepository repository;
    private final Map<UUID, PlayerProgress> progressByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> activeUnlocks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingUnlocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> saving = ConcurrentHashMap.newKeySet();
    private BukkitTask playtimeTask;
    private BukkitTask saveTask;
    private volatile CurrencyProvider currencyProvider;
    private volatile boolean ready;

    public ProgressionService(ChunkLockPlugin plugin, ClaimRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void initialize(Collection<PlayerProgress> storedProgress) {
        for (PlayerProgress progress : storedProgress) {
            progressByPlayer.put(progress.playerId(), progress);
        }
        ready = true;
    }

    public void start() {
        long saveInterval = Math.max(10L, plugin.getConfig().getLong(
                "progression.save-interval-seconds", 60L)) * 20L;
        playtimeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                record(player.getUniqueId(), ProgressTask.PLAYTIME, 1);
            }
        }, TICKS_PER_MINUTE, TICKS_PER_MINUTE);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            saveDirtyProgress();
        }, saveInterval, saveInterval);
    }

    public void stop() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        try {
            CompletableFuture.allOf(activeUnlocks.values().toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not finish pending claim-slot unlocks during shutdown: "
                    + exception.getMessage());
        }
        CompletableFuture<?>[] saves = progressByPlayer.values().stream()
                .filter(PlayerProgress::isDirty)
                .map(this::save)
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(saves).join();
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not persist all claim progression data during shutdown: "
                    + exception.getMessage());
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("progression.enabled", true);
    }

    public boolean tasksEnabled() {
        return isEnabled() && plugin.getConfig().getBoolean("progression.tasks.enabled", true);
    }

    public boolean economyEnabled() {
        return isEnabled() && plugin.getConfig().getBoolean("progression.economy.enabled", true)
                && currencyProvider != null;
    }

    public void setCurrencyProvider(CurrencyProvider currencyProvider) {
        this.currencyProvider = currencyProvider;
    }

    public int startingClaims() {
        return Math.max(0, plugin.getConfig().getInt("progression.starting-claims", 16));
    }

    public int maximumClaims() {
        return Math.max(startingClaims(), plugin.getConfig().getInt("progression.maximum-claims", 100));
    }

    public int claimLimit(UUID playerId, int permissionLimit) {
        if (!isEnabled()) {
            int defaultLimit = Math.max(0, plugin.getConfig().getInt("claims.default-limit", 16));
            return Math.min(maximumClaims(), Math.max(defaultLimit, permissionLimit));
        }
        long unlockedLimit = (long) startingClaims() + getProgress(playerId).unlockedClaims();
        return (int) Math.min(maximumClaims(), Math.max(unlockedLimit, permissionLimit));
    }

    public int unlockedClaims(UUID playerId) {
        return getProgress(playerId).unlockedClaims();
    }

    public long taskProgress(UUID playerId) {
        return getProgress(playerId).taskProgress();
    }

    public Objective currentObjective(UUID playerId) {
        PlayerProgress progress = getProgress(playerId);
        ProgressTask taskType = TASK_CYCLE.get(progress.unlockedClaims() % TASK_CYCLE.size());
        int difficultyTier = progress.unlockedClaims() / TASK_CYCLE.size();
        long baseTarget = switch (taskType) {
            case PLAYTIME -> Math.max(1L,
                    plugin.getConfig().getLong("progression.tasks.playtime.base-minutes", 15L));
            case BLOCKS -> Math.max(1L,
                    plugin.getConfig().getLong("progression.tasks.blocks.base-mined", 64L));
            case HOSTILE_MOBS -> Math.max(1L,
                    plugin.getConfig().getLong("progression.tasks.hostile-mobs.base-defeated", 5L));
        };
        double multiplier = Math.max(1.0,
                plugin.getConfig().getDouble("progression.tasks.difficulty-multiplier", 1.5));
        double scaledTarget = baseTarget * Math.pow(multiplier, difficultyTier);
        long target = scaledTarget >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, (long) Math.ceil(scaledTarget));
        return new Objective(taskType, target, Math.min(target, progress.taskProgress()));
    }

    public void record(UUID playerId, ProgressTask taskType, long amount) {
        if (!ready || !tasksEnabled() || pendingUnlocks.contains(playerId)
                || unlockedClaims(playerId) >= maximumClaims()) {
            return;
        }
        Objective objective = currentObjective(playerId);
        if (objective.task() != taskType || objective.progress() >= objective.target()) {
            return;
        }
        getProgress(playerId).addTaskProgress(amount, objective.target());
    }

    public UnlockResult unlockWithTask(Player player) {
        UUID playerId = player.getUniqueId();
        if (!ready) {
            return UnlockResult.NOT_READY;
        }
        if (!tasksEnabled()) {
            return UnlockResult.TASKS_DISABLED;
        }
        if (atMaximum(playerId)) {
            return UnlockResult.MAXIMUM_REACHED;
        }
        if (pendingUnlocks.contains(playerId)) {
            return UnlockResult.PENDING;
        }
        Objective objective = currentObjective(playerId);
        if (objective.progress() < objective.target()) {
            return UnlockResult.TASK_INCOMPLETE;
        }
        if (!pendingUnlocks.add(playerId)) {
            return UnlockResult.PENDING;
        }
        return persistUnlock(playerId, player, 0.0);
    }

    public UnlockResult unlockWithMoney(Player player) {
        if (!ready) {
            return UnlockResult.NOT_READY;
        }
        if (!isEnabled() || !plugin.getConfig().getBoolean("progression.economy.enabled", true)) {
            return UnlockResult.ECONOMY_DISABLED;
        }
        if (currencyProvider == null) {
            return UnlockResult.ECONOMY_UNAVAILABLE;
        }
        if (atMaximum(player.getUniqueId())) {
            return UnlockResult.MAXIMUM_REACHED;
        }
        if (!pendingUnlocks.add(player.getUniqueId())) {
            return UnlockResult.PENDING;
        }

        double cost = plugin.getConfig().getDouble("progression.economy.cost-per-claim", 5000.0);
        if (!Double.isFinite(cost) || cost <= 0.0) {
            pendingUnlocks.remove(player.getUniqueId());
            plugin.getLogger().severe("progression.economy.cost-per-claim must be a positive number.");
            return UnlockResult.INVALID_COST;
        }
        CurrencyProvider economy = currencyProvider;
        if (!economy.withdraw(player, cost)) {
            pendingUnlocks.remove(player.getUniqueId());
            return UnlockResult.INSUFFICIENT_FUNDS;
        }
        return persistUnlock(player.getUniqueId(), player, cost, economy);
    }

    public String formatCurrency(double amount) {
        CurrencyProvider provider = currencyProvider;
        return provider == null ? Double.toString(amount) : provider.format(amount);
    }

    private boolean atMaximum(UUID playerId) {
        return claimLimit(playerId, 0) >= maximumClaims();
    }

    private PlayerProgress getProgress(UUID playerId) {
        return progressByPlayer.computeIfAbsent(playerId, id -> new PlayerProgress(id, 0, 0L));
    }

    private UnlockResult persistUnlock(UUID playerId, Player purchaser, double purchaseCost) {
        return persistUnlock(playerId, purchaser, purchaseCost, null);
    }

    private UnlockResult persistUnlock(UUID playerId, Player purchaser, double purchaseCost,
                                       CurrencyProvider economy) {
        PlayerProgress previous = getProgress(playerId);
        PlayerProgress updated = previous.snapshot();
        updated.unlockClaim();
        try {
            CompletableFuture<Void> operation = repository.saveProgress(updated).whenComplete((ignored, error) -> {
                if (error != null) {
                    plugin.getLogger().severe("Could not save claim progression for " + playerId + ": "
                            + error.getMessage());
                    if (purchaser != null && economy != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                economy.refund(purchaser, purchaseCost);
                                if (purchaser.isOnline()) {
                                    plugin.messages().send(purchaser, "progression-save-failed");
                                }
                                pendingUnlocks.remove(playerId);
                            } catch (RuntimeException refundError) {
                                plugin.getLogger().severe("Could not refund failed claim-slot purchase for "
                                        + playerId + ": " + refundError.getMessage());
                                pendingUnlocks.remove(playerId);
                            }
                        });
                    } else if (purchaser != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (purchaser.isOnline()) {
                                plugin.messages().send(purchaser, "progression-save-failed");
                            }
                            pendingUnlocks.remove(playerId);
                        });
                    } else {
                        pendingUnlocks.remove(playerId);
                    }
                    activeUnlocks.remove(playerId);
                    return;
                }
                progressByPlayer.put(playerId, updated);
                pendingUnlocks.remove(playerId);
                if (purchaser != null && purchaser.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(purchaser,
                            economy == null ? "claim-slot-unlocked" : "claim-slot-purchased",
                            economy == null ? Map.of("claims", Integer.toString(claimLimit(playerId, 0)))
                                    : Map.of("claims", Integer.toString(claimLimit(playerId, 0)),
                                    "cost", economy.format(purchaseCost))));
                }
                activeUnlocks.remove(playerId);
            });
            activeUnlocks.put(playerId, operation);
            if (operation.isDone()) {
                activeUnlocks.remove(playerId, operation);
            }
        } catch (RuntimeException exception) {
            pendingUnlocks.remove(playerId);
            if (purchaser != null && economy != null) {
                economy.refund(purchaser, purchaseCost);
            }
            throw exception;
        }
        return UnlockResult.SAVING;
    }

    private CompletableFuture<Void> save(PlayerProgress progress) {
        return repository.saveProgress(progress);
    }

    private void saveDirtyProgress() {
        for (PlayerProgress progress : progressByPlayer.values()) {
            if (progress.isDirty() && !pendingUnlocks.contains(progress.playerId())
                    && saving.add(progress.playerId())) {
                save(progress).whenComplete((ignored, error) -> {
                    saving.remove(progress.playerId());
                    if (error != null) {
                        plugin.getLogger().severe("Could not save task progress for "
                                + progress.playerId() + ": " + error.getMessage());
                    }
                });
            }
        }
    }

    public record Objective(ProgressTask task, long target, long progress) {
    }

    public enum UnlockResult {
        SAVING,
        TASK_INCOMPLETE,
        TASKS_DISABLED,
        ECONOMY_DISABLED,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        INVALID_COST,
        MAXIMUM_REACHED,
        PENDING,
        NOT_READY
    }
}
