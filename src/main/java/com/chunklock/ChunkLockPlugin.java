package com.chunklock;

import com.chunklock.command.ChunkCommand;
import com.chunklock.listener.ProtectionListener;
import com.chunklock.service.ClaimService;
import com.chunklock.storage.ClaimRepository;
import com.chunklock.util.Messages;
import com.chunklock.visual.ClaimVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.CompletionException;

public final class ChunkLockPlugin extends JavaPlugin {
    private ClaimRepository repository;
    private ClaimService claimService;
    private Messages messages;
    private ClaimVisualizer visualizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        reloadConfig();

        repository = new ClaimRepository(this);
        claimService = new ClaimService(repository);
        messages = new Messages(this);
        visualizer = new ClaimVisualizer(this, claimService);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        ChunkCommand chunkCommand = new ChunkCommand(this);
        getCommand("chunk").setExecutor(chunkCommand);
        getCommand("chunk").setTabCompleter(chunkCommand);
        visualizer.start();

        repository.initializeAndLoad().whenComplete((claims, error) -> {
            if (!isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    getLogger().severe("Could not initialize claim storage: " + cause.getMessage());
                    cause.printStackTrace();
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                claimService.initialize(claims);
                getLogger().info("Loaded " + claims.size() + " chunk claim(s).");
            });
        });
    }

    @Override
    public void onDisable() {
        if (visualizer != null) {
            visualizer.stop();
        }
        if (repository != null) {
            repository.close();
        }
    }

    public ClaimService claims() {
        return claimService;
    }

    public Messages messages() {
        return messages;
    }

    public ClaimVisualizer visualizer() {
        return visualizer;
    }

    public void reportSaveResult(org.bukkit.entity.Player player, java.util.concurrent.CompletableFuture<Void> save) {
        save.whenComplete((ignored, error) -> {
            if (error == null || !isEnabled()) {
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            getLogger().severe("Failed to persist a claim change: " + cause.getMessage());
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) {
                    messages.send(player, "save-failed");
                }
            });
        });
    }
}
