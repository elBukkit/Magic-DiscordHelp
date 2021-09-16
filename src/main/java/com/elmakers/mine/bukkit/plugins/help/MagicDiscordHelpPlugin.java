package com.elmakers.mine.bukkit.plugins.help;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.magic.MagicPlugin;

import net.dv8tion.jda.api.JDA;

public class MagicDiscordHelpPlugin extends JavaPlugin {
    private String token;
    private String channel;
    private String reactionChannel;
    private String ignoreChannel;
    private String reactionEmote;
    private String joinRole;
    private String guildId;
    private String command;
    private JDA jda = null;
    private MagicController magic;

    public void onEnable() {
        saveDefaultConfig();

        Plugin magicPlugin = getServer().getPluginManager().getPlugin("Magic");
        if (magicPlugin == null || !magicPlugin.isEnabled() || !(magicPlugin instanceof MagicPlugin)) {
            getLogger().warning("Magic is not enabled, shutting down");
            return;
        }
        this.magic = ((MagicPlugin)magicPlugin).getController();
        YamlConfiguration messagesConfig = new YamlConfiguration();
        try {
            messagesConfig.load(new InputStreamReader(getResource("messages.yml"), "UTF-8"));
            magic.getMessages().load(messagesConfig);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to load messages.yml resource", ex);
        }

        token = getConfig().getString("token", "");
        channel = getConfig().getString("channel", "");
        reactionChannel = getConfig().getString("reaction_channel", "");
        ignoreChannel = getConfig().getString("ignore_channel", "");
        reactionEmote = getConfig().getString("reaction_emote", "");
        guildId = getConfig().getString("guild", "");
        joinRole = getConfig().getString("join_role", "");
        command = getConfig().getString("command", "");
        if (token == null || token.isEmpty()) {
            getLogger().warning("Please put your bot token in config.yml, otherwise this plugin can't work");
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, new JDAConnector(this));
        }
    }

    public void onDisable() {
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    public String getToken() {
        return token;
    }

    public String getChannel() {
        return channel;
    }

    public String getReactionChannel() {
        return reactionChannel;
    }

    public String getIgnoreChannel() {
        return ignoreChannel;
    }

    public String getReactionEmote() {
        return reactionEmote;
    }

    public String getJoinRole() {
        return joinRole;
    }

    public String getCommand() {
        return command;
    }

    public String getGuild() {
        return guildId;
    }

    public MagicController getMagic() {
        return magic;
    }

    protected void setJDA(JDA jda) {
        this.jda = jda;
        getLogger().info("Connected to the Discord server!");
    }
}
