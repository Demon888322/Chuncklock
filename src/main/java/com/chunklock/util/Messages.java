package com.chunklock.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Messages {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final YamlConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaults = plugin.getResource("messages.yml")) {
            if (defaults == null) {
                throw new IllegalStateException("Default messages.yml is missing from the plugin JAR.");
            }
            messages.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8)));
            messages.options().copyDefaults(true);
            messages.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update messages.yml with new default messages.", exception);
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String prefix = messages.getString("prefix", "");
        String message = messages.getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return;
        }
        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((name, value) -> resolver.resolver(Placeholder.unparsed(name, value)));
        sender.sendMessage(MINI_MESSAGE.deserialize(prefix + message, resolver.build()));
    }
}
