package com.elmakers.mine.bukkit.plugins.help;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.elmakers.mine.bukkit.magic.MagicController;

public class CommandProcessor implements TabExecutor {
    private final Plugin plugin;
    private final MagicController magic;

    public CommandProcessor(Plugin plugin, MagicController magic) {
        this.plugin = plugin;
        this.magic = magic;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        try {
            onEvaluate(commandSender);
        } catch (Exception ex) {
            commandSender.sendMessage(ChatColor.RED + "Something went wrong!");
            plugin.getLogger().log(Level.SEVERE, "Error loading evaluation goals", ex);
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return new ArrayList<>();
    }

    private void onEvaluate(CommandSender sender)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration goals = new YamlConfiguration();
        goals.load(new InputStreamReader(plugin.getResource("goals.yml")));
        EvaluateTask evaluateTask = new EvaluateTask(sender, plugin, magic, goals);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, evaluateTask);
    }
}
