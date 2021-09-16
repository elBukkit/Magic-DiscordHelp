package com.elmakers.mine.bukkit.plugins.help;

import java.util.EnumSet;
import java.util.logging.Level;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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
            String token = controller.getToken();
            JDA jda = JDABuilder.create(intents)
                    .setAutoReconnect(true)
                    .setToken(token)
                    .addEventListeners(new DiscordChatListener(controller))
                    .build();

            jda.awaitReady();
            controller.setJDA(jda);
        } catch (Exception ex) {
            controller.getLogger().log(Level.SEVERE, "An unexpected error occurred connecting to the Discord server", ex);
        }
    }
}
