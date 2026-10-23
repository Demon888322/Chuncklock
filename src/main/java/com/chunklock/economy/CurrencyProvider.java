package com.chunklock.economy;

import org.bukkit.entity.Player;

public interface CurrencyProvider {
    boolean withdraw(Player player, double amount);

    void refund(Player player, double amount);

    String format(double amount);
}
