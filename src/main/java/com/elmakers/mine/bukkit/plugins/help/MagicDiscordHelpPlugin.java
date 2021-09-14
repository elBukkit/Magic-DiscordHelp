package com.elmakers.mine.bukkit.plugins.help;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.magic.MagicPlugin;

import net.dv8tion.jda.api.JDA;

public class MagicDiscordHelpPlugin extends JavaPlugin {
    private String token;
    private String channel;
    private JDA jda = null;
    private MagicController magic;

    public void onEnable() {
        saveDefaultConfig();

        Plugin magicPlugin = getServer().getPluginManager().getPlugin("Magic");
        if (magicPlugin == null || !magicPlugin.isEnabled() || !(magicPlugin instanceof MagicPlugin)) {
            getLogger().warning("Magic is not enabled, shutting down");
            return;
        }
        this.magic = (MagicController)((MagicPlugin)magicPlugin).getController();

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

    public MagicController getMagic() {
        return magic;
    }

    protected void setJDA(JDA jda) {
        this.jda = jda;
        getLogger().info("Connected to the Discord server!");
    }
}
