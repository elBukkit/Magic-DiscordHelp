package com.elmakers.mine.bukkit.plugins.help;

import java.util.EnumSet;
import java.util.logging.Level;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class JDAConnector implements Runnable {
    private final EnumSet<GatewayIntent> intents = EnumSet.of(
        GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_MEMBERS
    );
    private final MagicDiscordHelpPlugin controller;

    public JDAConnector(MagicDiscordHelpPlugin controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        try {
            DiscordChatListener listener = new DiscordChatListener(controller);
            String token = controller.getToken();
            JDA jda = JDABuilder.create(intents)
                    .setAutoReconnect(true)
                    .setToken(token)
                    .addEventListeners(listener)
                    .build();

            jda.awaitReady();
            controller.setJDA(jda);
            String guildId = controller.getGuild();
            if (!guildId.isEmpty()) {
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    controller.getLogger().warning("Could not find guild with id: " + guildId);
                } else {
                    listener.registerCommands(guild);
                }
            }
        } catch (Exception ex) {
            controller.getLogger().log(Level.SEVERE, "An unexpected error occurred connecting to the Discord server", ex);
        }
    }
}
