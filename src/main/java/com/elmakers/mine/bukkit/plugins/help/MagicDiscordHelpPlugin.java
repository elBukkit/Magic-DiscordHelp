package com.elmakers.mine.bukkit.plugins.help;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.magic.MagicPlugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class MagicDiscordHelpPlugin extends JavaPlugin {
    private String token;
    private String channel;
    private String commandChannel;
    private String mentionChannel;
    private String mentionId;
    private String reactionChannel;
    private String ignoreChannel;
    private String reactionEmote;
    private Emoji reactionEmoji;
    private String joinRole;
    private String joinChannel;
    private String guildId;
    private String command;
    private ConfigurationSection responseChannels;
    private boolean debug;
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
        commandChannel = getConfig().getString("command_channel", "*");
        reactionChannel = getConfig().getString("reaction_channel", "*");
        mentionChannel = getConfig().getString("mention_channel", "*");
        ignoreChannel = getConfig().getString("ignore_channel", "");
        reactionEmote = getConfig().getString("reaction_emote", "");
        if (!reactionEmote.isEmpty()) {
            reactionEmoji = Emoji.fromFormatted(reactionEmote);
        }
        guildId = getConfig().getString("guild", "");
        joinRole = getConfig().getString("join_role", "");
        joinChannel = getConfig().getString("join_channel", "");
        mentionId = getConfig().getString("mention_id", "");
        command = getConfig().getString("command", "mhelp");
        debug = getConfig().getBoolean("debug", false);
        responseChannels = getConfig().getConfigurationSection("response_channels");
        if (token == null || token.isEmpty()) {
            getLogger().warning("Please put your bot token in config.yml, otherwise this plugin can't work");
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, new JDAConnector(this));
        }

        if (joinChannel != null && !joinChannel.isEmpty()) {
            getLogger().info("Sending join messages to " + joinChannel);
        }

        CommandProcessor processor = new CommandProcessor(this, magic);
        getCommand("evaluate").setTabCompleter(processor);
        getCommand("evaluate").setExecutor(processor);
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

    public String getWelcomeChannel() {
        return joinChannel != null && !joinChannel.isEmpty() ? joinChannel : channel;
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

    public Emoji getReactionEmoji() {
        return reactionEmoji;
    }

    public String getCommandChannel() {
        return commandChannel;
    }

    public String getMentionChannel() {
        return mentionChannel;
    }

    public String getMentionId() {
        return mentionId;
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

    public String getChannelResponse(String channel) {
        return responseChannels == null ? null : responseChannels.getString(channel);
    }

    public boolean isDebug() {
        return debug;
    }

    public MagicController getMagic() {
        return magic;
    }

    protected void setJDA(JDA jda) {
        this.jda = jda;
        jda.getPresence().setActivity(Activity.playing(magic.getMessages().get("discord.status")));
        getLogger().info("Connected to the Discord server!");
    }
}
