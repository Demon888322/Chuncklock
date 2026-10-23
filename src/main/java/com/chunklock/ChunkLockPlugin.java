package com.chunklock;

import com.chunklock.command.ChunkCommand;
import com.chunklock.economy.CurrencyProvider;
import com.chunklock.listener.ProtectionListener;
import com.chunklock.service.ClaimService;
import com.chunklock.service.ProgressionService;
import com.chunklock.storage.ClaimRepository;
import com.chunklock.util.Messages;
import com.chunklock.visual.ClaimVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;

public final class ChunkLockPlugin extends JavaPlugin {
    private ClaimRepository repository;
    private ClaimService claimService;
    private ProgressionService progressionService;
    private Messages messages;
    private ClaimVisualizer visualizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();

        repository = new ClaimRepository(this);
        claimService = new ClaimService(repository);
        progressionService = new ProgressionService(this, repository);
        messages = new Messages(this);
        visualizer = new ClaimVisualizer(this, claimService);
        progressionService.setCurrencyProvider(loadCurrencyProvider());

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        ChunkCommand chunkCommand = new ChunkCommand(this);
        getCommand("chunk").setExecutor(chunkCommand);
        getCommand("chunk").setTabCompleter(chunkCommand);
        visualizer.start();
        progressionService.start();

        repository.initializeAndLoad().whenComplete((data, error) -> {
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
                claimService.initialize(data.claims());
                progressionService.initialize(data.playerProgress());
                getLogger().info("Loaded " + data.claims().size() + " chunk claim(s) and progression for "
                        + data.playerProgress().size() + " player(s).");
            });
        });
    }

    @Override
    public void onDisable() {
        if (visualizer != null) {
            visualizer.stop();
        }
        if (progressionService != null) {
            progressionService.stop();
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

    public ProgressionService progression() {
        return progressionService;
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

    private CurrencyProvider loadCurrencyProvider() {
        if (!getConfig().getBoolean("progression.economy.enabled", true)) {
            getLogger().info("Claim-slot currency unlocks are disabled in config.yml.");
            return null;
        }
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            getLogger().info("Vault was not found; task-based claim unlocks remain available.");
            return null;
        }
        try {
            Class<?> providerClass = Class.forName(
                    "com.chunklock.economy.VaultEconomyProvider", true, getClass().getClassLoader());
            Object provider = providerClass.getMethod("create", JavaPlugin.class).invoke(null, this);
            if (provider == null) {
                getLogger().warning("Vault is installed, but no economy provider is registered. "
                        + "Task-based claim unlocks remain available.");
                return null;
            }
            getLogger().info("Vault economy integration is ready for claim-slot purchases.");
            return (CurrencyProvider) provider;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            getLogger().warning("Could not load Vault economy integration; money unlocks are disabled: "
                    + exception.getMessage());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            getLogger().warning("Vault economy integration failed; money unlocks are disabled: "
                    + cause.getMessage());
        } catch (LinkageError exception) {
            getLogger().warning("Vault API is unavailable; money unlocks are disabled: "
                    + exception.getMessage());
        }
        return null;
    }
}
