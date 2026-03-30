package io.github.maste.customclans.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageManager {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration configuration;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        File messageFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration loadedConfiguration = YamlConfiguration.loadConfiguration(messageFile);
        try (InputStream defaultsStream = plugin.getResource("messages.yml")) {
            if (defaultsStream != null) {
                YamlConfiguration defaultConfiguration = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
                );
                loadedConfiguration.setDefaults(defaultConfiguration);
                loadedConfiguration.options().copyDefaults(true);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load default messages.yml from plugin resources: " + exception.getMessage());
        }

        this.configuration = loadedConfiguration;
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    public String raw(String path) {
        return configuration.getString(path, "<red>Missing message: " + path + "</red>");
    }

    public Component component(String path, TagResolver... extraResolvers) {
        return miniMessage.deserialize(raw(path), withPrefix(extraResolvers));
    }

    public List<Component> componentList(String path, TagResolver... extraResolvers) {
        List<String> rawList = configuration.getStringList(path);
        List<Component> components = new ArrayList<>(rawList.size());
        TagResolver resolver = withPrefix(extraResolvers);
        for (String line : rawList) {
            components.add(miniMessage.deserialize(line, resolver));
        }
        return components;
    }

    public void send(CommandSender sender, String path, TagResolver... extraResolvers) {
        sender.sendMessage(component(path, extraResolvers));
    }

    public void sendList(CommandSender sender, String path, TagResolver... extraResolvers) {
        for (Component component : componentList(path, extraResolvers)) {
            sender.sendMessage(component);
        }
    }

    private TagResolver withPrefix(TagResolver... extraResolvers) {
        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.parsed("prefix", raw("general.prefix")));
        for (TagResolver extraResolver : extraResolvers) {
            resolvers.add(extraResolver);
        }
        return TagResolver.resolver(resolvers);
    }
}
