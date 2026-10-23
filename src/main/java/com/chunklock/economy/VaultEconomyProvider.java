package com.chunklock.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyProvider implements CurrencyProvider {
    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    public static CurrencyProvider create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomyProvider(registration.getProvider());
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public void refund(Player player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            throw new IllegalStateException("Economy provider rejected a claim-purchase refund: "
                    + response.errorMessage);
        }
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
