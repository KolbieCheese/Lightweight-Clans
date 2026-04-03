package io.github.maste.customclans.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void rawFallsBackToBundledDefaultsWhenServerMessagesFileIsOutdated() throws Exception {
        Files.writeString(tempDir.resolve("messages.yml"), """
                general:
                  prefix: "<gold><bold>Clans</bold></gold><gray> > </gray>"
                """);

        MessageManager messageManager = new MessageManager(mockPlugin(tempDir));

        assertEquals(
                "<prefix><green>Updated clan description.</green>",
                messageManager.raw("description.success")
        );
        assertEquals(
                "<prefix><green>Lightweight Clans configuration reloaded successfully.</green>",
                messageManager.raw("reload.success")
        );
        assertEquals(
                "<gold><bold>Active Clans</bold></gold>",
                messageManager.raw("list.header")
        );
        assertEquals(
                "<gray>- <white><name></white> <dark_gray>[<tag>]</dark_gray> <gray>(<members> members)</gray>",
                messageManager.raw("list.line")
        );
    }

    @Test
    void componentListFallsBackToBundledDefaultsWhenServerMessagesFileIsOutdated() throws Exception {
        Files.writeString(tempDir.resolve("messages.yml"), """
                general:
                  prefix: "<gold><bold>Clans</bold></gold><gray> > </gray>"
                """);

        MessageManager messageManager = new MessageManager(mockPlugin(tempDir));

        var lines = messageManager.componentList(
                "info.lines",
                Placeholder.unparsed("name", "Crimson Knights"),
                Placeholder.unparsed("tag", "CK"),
                Placeholder.unparsed("color", "Red"),
                Placeholder.unparsed("president", "Alice"),
                Placeholder.unparsed("description", "Raid-focused PvE clan"),
                Placeholder.unparsed("member_count", "3"),
                Placeholder.unparsed("max_members", "20"),
                Placeholder.unparsed("online_count", "2")
        );

        assertFalse(lines.isEmpty());
        assertEquals(7, lines.size());
        assertEquals(
                "Name: Crimson Knights",
                PlainTextComponentSerializer.plainText().serialize(lines.getFirst())
        );
    }

    @Test
    void componentListAddsDescriptionLineToOlderInfoLists() throws Exception {
        Files.writeString(tempDir.resolve("messages.yml"), """
                general:
                  prefix: "<gold><bold>Clans</bold></gold><gray> > </gray>"
                info:
                  lines:
                    - "<gray>Name: <white><name></white>"
                    - "<gray>Tag: <white>[<tag>]</white>"
                    - "<gray>Color: <white><color></white>"
                    - "<gray>President: <white><president></white>"
                    - "<gray>Members: <white><member_count>/<max_members></white>"
                    - "<gray>Online: <white><online_count></white>"
                """);

        MessageManager messageManager = new MessageManager(mockPlugin(tempDir));

        var lines = messageManager.componentList(
                "info.lines",
                Placeholder.unparsed("name", "Crimson Knights"),
                Placeholder.unparsed("tag", "CK"),
                Placeholder.unparsed("color", "Red"),
                Placeholder.unparsed("president", "Alice"),
                Placeholder.unparsed("description", "Raid-focused PvE clan"),
                Placeholder.unparsed("member_count", "3"),
                Placeholder.unparsed("max_members", "20"),
                Placeholder.unparsed("online_count", "2")
        );

        assertEquals(7, lines.size());
        assertEquals(
                "Description: Raid-focused PvE clan",
                PlainTextComponentSerializer.plainText().serialize(lines.get(4))
        );
    }

    @Test
    void componentListRestoresMissingHelpLinesFromBundledDefaults() throws Exception {
        Files.writeString(tempDir.resolve("messages.yml"), """
                general:
                  prefix: "<gold><bold>Clans</bold></gold><gray> > </gray>"
                help:
                  lines:
                    - "<gold><bold>Lightweight Clans Commands</bold></gold>"
                    - "<yellow>/clan help</yellow><gray> - Show this help list.</gray>"
                    - "<yellow>/clan create <clan name></yellow><gray> - Create a new clan and become its President.</gray>"
                    - "<yellow>/clan invite <player></yellow><gray> - Invite an online player to your clan (any clan member).</gray>"
                """);

        MessageManager messageManager = new MessageManager(mockPlugin(tempDir));

        var lines = messageManager.componentList("help.lines");
        var serializedLines = lines.stream()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .toList();
        var adminLines = messageManager.componentList("help.admin-lines");
        var serializedAdminLines = adminLines.stream()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .toList();

        assertFalse(lines.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                serializedLines.stream().anyMatch(line -> line.contains("Set your clan description"))
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                serializedAdminLines.stream().anyMatch(line -> line.contains("/clan reload"))
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                serializedAdminLines.stream().anyMatch(line -> line.contains("/clan admin rename"))
        );
    }

    private JavaPlugin mockPlugin(Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File(dataFolder.toString()));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MessageManagerTest"));
        when(plugin.getResource("messages.yml")).thenAnswer(invocation -> openBundledMessages());
        return plugin;
    }

    private InputStream openBundledMessages() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("messages.yml");
        if (inputStream == null) {
            throw new IllegalStateException("Test resource messages.yml is missing from the classpath");
        }
        return inputStream;
    }
}
