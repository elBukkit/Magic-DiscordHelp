package com.elmakers.mine.bukkit.plugins.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;

public class DiscordChatListener extends ListenerAdapter {
    private static final Pattern MHELP_PATTERN = Pattern.compile("/mhelp ([a-z_A-Z\\.]*)");
    private final MagicDiscordHelpPlugin controller;
    private final Help help;

    public DiscordChatListener(MagicDiscordHelpPlugin controller) {
        this.controller = controller;
        help = controller.getMagic().getMessages().getHelp();
    }

    protected void respond(Message originalMessage, String message, List<String> buttonIds, List<String> buttonLabels) {
        message = translateMessage(message);
        MessageAction action = originalMessage.reply(message);
        Button[] buttons = new Button[buttonIds.size()];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = Button.primary("help:" + buttonIds.get(i), buttonLabels.get(i));
        }
        action.setActionRow(buttons);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in channel " + originalMessage.getChannel(), throwable));
    }

    protected void addTopics(MessageAction action, String message) {
        List<Button> buttons = getTopicButtons(message);
        if (!buttons.isEmpty()) {
            action.setActionRow(buttons);
        }
    }

    protected List<Button> getTopicButtons(String message) {
        List<Button> buttons = new ArrayList<>();
        Matcher m = MHELP_PATTERN.matcher(message);
        while (m.find()) {
            String topicKey = m.group(1);
            HelpTopic topic = help.getTopic(topicKey);
            if (topic == null) continue;
            Button button = Button.primary("help:" + topicKey, topic.getTitle());
            buttons.add(button);
        }
        return buttons;
    }

    protected void respond(Message authorMessage, String message) {
        message = translateMessage(message);
        MessageAction action = authorMessage.reply(message);
        addTopics(action, message);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message to channel " + authorMessage.getChannel().getName(), throwable));
    }

    protected void respond(Message authorMessage, HelpTopic topic) {
        respond(authorMessage, getTopicMessage(topic));
    }

    protected String getTopicMessage(HelpTopic topic) {
        String topicText = topic.getText();
        topicText = getSimpleMessage(topicText);
        return topicText;
    }

    protected String getSimpleMessage(String message) {
        return ChatUtils.getSimpleMessage(message, false, " **", "**");
    }

    protected String translateMessage(String message) {
        // Oh yeah hacks sue me
        message = message.replace("➽", "");
        if (message.length() >= 2000) {
            message = message.substring(0, 1996) + "...";
        }
        return ChatColor.stripColor(message);
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        if (event.getChannel().getName().equals(controller.getIgnoreChannel())) return;
        Button button = event.getButton();
        if (button == null) return;
        String id = button.getId();
        if (id == null) return;
        if (!id.startsWith("help:")) return;
        final String topicId = id.substring(5);
        HelpTopic topic = help.getTopic(topicId);
        if (topic != null) {
            String message = getTopicMessage(topic);
            message = translateMessage(message);
            Member clicker = event.getMember();
            if (clicker != null) {
                message += "\n    *for: " + event.getMember().getAsMention() + "*";
            }
            ReplyAction action = event.reply(message);
            List<Button> buttons = getTopicButtons(message);
            if (!buttons.isEmpty()) {
                action.addActionRow(buttons);
            }
            action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + id, throwable));
        } else {
            event.reply("Could not find help topic: " + id).queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click: " + topicId, throwable));
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        MessageChannel channel = event.getChannel();
        if (channel.getName().equals(controller.getIgnoreChannel())) return;
        String reactionChanel = controller.getReactionChannel();
        if (!reactionChanel.equals("*") && !channel.getName().equals(reactionChanel)) return;
        String reactionCode = event.getReaction().getReactionEmote().getAsReactionCode();
        reactionCode = StringUtils.split(reactionCode, ":")[0];
        if (!reactionCode.equals(controller.getReactionEmote())) return;
        event.retrieveMessage().queue(this::respondToMessage);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot()) return;
        MessageChannel channel = event.getChannel();
        if (channel.getName().equals(controller.getIgnoreChannel())) return;
        if (!channel.getName().equals(controller.getChannel())) return;
        Message message = event.getMessage();
        respondToMessage(message);
    }

    protected void respondToMessage(Message message) {
        String msg = message.getContentDisplay();
        String[] pieces = ChatUtils.getWords(msg);
        if (pieces.length == 0) return;

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
        if (matches.isEmpty()) {
            respond(message, "404: Sorry I have no idea what you're talking about!");
            return;
        }
        if (matches.size() == 1) {
            respond(message, matches.get(0).getTopic());
            return;
        }
        Collections.sort(matches);
        StringBuilder sb = new StringBuilder();
        sb.append("Found ");
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
            String summary = match.getSummary(keywords, title, 100, "**", "**");
            buttonLabels.add(title);
            buttonIds.add(match.getTopic().getKey());
            sb.append("\n");
            sb.append(title);
            sb.append(" : ");
            sb.append(summary);
        }

        respond(message, sb.toString(), buttonIds, buttonLabels);
    }
}
