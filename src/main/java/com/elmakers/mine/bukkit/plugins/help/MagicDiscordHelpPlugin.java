package com.elmakers.mine.bukkit.plugins.help;

import org.bukkit.plugin.java.JavaPlugin;

import net.dv8tion.jda.api.JDA;

public class MagicDiscordHelpPlugin extends JavaPlugin {
    private String token;
    private String channel;
    private JDA jda = null;

    public void onEnable() {
        saveDefaultConfig();
        token = getConfig().getString("token");
        channel = getConfig().getString("channel");
        if (token == null || token.isEmpty()) {
            getLogger().warning("Please put your bot token in config.yml, otherwise this plugin can't work");
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, new JDAConnector(this));
        }
    }

    public void onDisable() {
    }

    public String getToken() {
        return token;
    }

    public String getChannel() {
        return channel;
    }

    protected void setJDA(JDA jda) {
        this.jda = jda;
        getLogger().info("Connected to the Discord server!");
    }
}