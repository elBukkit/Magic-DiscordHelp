package com.elmakers.mine.bukkit.plugins.help;

import java.util.logging.Level;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageAction;

public class DiscordChatListener extends ListenerAdapter {
    private final MagicDiscordHelpPlugin controller;

    public DiscordChatListener(MagicDiscordHelpPlugin controller) {
        this.controller = controller;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot()) return;
        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();
        if (!channel.getName().equals(controller.getChannel())) return;

        String msg = message.getContentDisplay();
        controller.getLogger().info("Got message: " + msg);
        MessageAction action = channel.sendMessage("Well hello there " + author.getName() + "!");
        action.queue(sentMessage -> controller.getLogger().info("  Responded!"), throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + channel.getName(), throwable));
    }
}
