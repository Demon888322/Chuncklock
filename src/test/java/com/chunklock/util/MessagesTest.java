package com.chunklock.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagesTest {
    @TempDir
    Path dataFolder;

    @Test
    void addsNewDefaultMessagesWithoutOverwritingServerCustomizations() throws Exception {
        Path messageFile = dataFolder.resolve("messages.yml");
        Files.writeString(messageFile, """
                prefix: "<blue>[My Server]</blue> "
                messages:
                  claimed: "<green>Custom claim message.</green>"
                """);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource("messages.yml")).thenReturn(new ByteArrayInputStream("""
                prefix: "<gray>[ChunkLock]</gray> "
                messages:
                  claimed: "<green>Claimed.</green>"
                  progression-status: "<gray>Slots: <claims>/<maximum></gray>"
                """.getBytes(StandardCharsets.UTF_8)));

        new Messages(plugin);

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(messageFile.toFile());
        assertEquals("<blue>[My Server]</blue> ", saved.getString("prefix"));
        assertEquals("<green>Custom claim message.</green>", saved.getString("messages.claimed"));
        assertEquals("<gray>Slots: <claims>/<maximum></gray>",
                saved.getString("messages.progression-status"));
    }
}
