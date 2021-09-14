package com.elmakers.mine.bukkit.plugins.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;

import com.elmakers.mine.bukkit.ChatUtils;
import com.elmakers.mine.bukkit.utility.help.Help;
import com.elmakers.mine.bukkit.utility.help.HelpTopic;
import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;

public class DiscordChatListener extends ListenerAdapter {
    private final MagicDiscordHelpPlugin controller;
    private final Help help;

    public DiscordChatListener(MagicDiscordHelpPlugin controller) {
        this.controller = controller;
        help = controller.getMagic().getMessages().getHelp();
    }

    protected void send(MessageChannel channel, String message) {
        message = translateMessage(message);
        MessageAction action = channel.sendMessage(message);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + channel.getName(), throwable));
    }

    protected void sendTopic(MessageChannel channel, String message, List<String> buttonIds, List<String> buttonLabels) {
        message = translateMessage(message);
        MessageAction action = channel.sendMessage(message);
        Button[] buttons = new Button[buttonIds.size()];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = Button.primary("navigate:" + buttonIds.get(i), buttonLabels.get(i));
        }
        action.setActionRow(buttons);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + channel.getName(), throwable));
    }

    protected void respond(Message authorMessage, String message) {
        message = translateMessage(message);
        MessageAction action = authorMessage.reply(message);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + authorMessage.getChannel().getName(), throwable));
    }

    protected void respond(Message authorMessage, HelpTopic topic) {
        respond(authorMessage, getTopicMessage(topic));
    }

    protected void respond(MessageChannel channel, Member member, String message) {
        message = translateMessage(message);
        MessageAction action = channel.sendMessage(message);
        action.mention(member);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + channel.getName(), throwable));
    }

    protected void respond(MessageChannel channel, Member member, HelpTopic topic) {
        String topicText = topic.getText();
        topicText = ChatUtils.getSimpleMessage(topicText);
        respond(channel, member, topicText);
    }

    protected String getTopicMessage(HelpTopic topic) {
        String topicText = topic.getText();
        topicText = ChatUtils.getSimpleMessage(topicText);
        return topicText;
    }

    protected String translateMessage(String message) {
        // Oh yeah hacks sue me
        message = message.replace("➽", "");
        return ChatColor.stripColor(message);
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        Button button = event.getButton();
        if (button == null) return;
        String id = button.getId();
        if (id == null) return;
        if (!id.startsWith("navigate:")) return;
        final String topicId = id.substring(9);
        HelpTopic topic = help.getTopic(topicId);
        if (topic != null) {
            String message = getTopicMessage(topic);
            message = translateMessage(message);
            event.reply(message).queue();
        } else {
            event.reply("Could not find help topic: " + id).queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click: " + topicId, throwable));
        }
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


        String[] pieces = StringUtils.split(msg, " ");
        if (pieces.length == 1) {
            HelpTopic topic = help.getTopic(pieces[0]);
            if (topic != null) {
                respond(message, topic);
                return;
            }
        }
        List<String> keywords = new ArrayList<>();
        for (String arg : pieces) {
            keywords.add(arg.toLowerCase());
        }
        List<HelpTopicMatch> matches = help.findMatches(keywords);
        Collections.sort(matches);
        StringBuilder sb = new StringBuilder();
        sb.append("Found");
        sb.append(matches.size());
        sb.append(" matches");
        if (matches.size() > 5) {
            sb.append(" (showing top 5)");
        }
        List<String> buttonLabels = new ArrayList<>();
        List<String> buttonIds = new ArrayList<>();
        int count = 0;
        for (HelpTopicMatch match : matches) {
            if (count++ >= 5) break;
            String title = match.getTopic().getTitle();
            String summary = match.getSummary(keywords, title, 100);
            buttonLabels.add(title);
            buttonIds.add(match.getTopic().getKey());
            sb.append("\n");
            sb.append(title);
            sb.append(" : ");
            sb.append(summary);
        }

        sendTopic(channel, sb.toString(), buttonIds, buttonLabels);
    }
}
