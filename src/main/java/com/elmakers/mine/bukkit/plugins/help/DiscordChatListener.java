package com.elmakers.mine.bukkit.plugins.help;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;

import com.elmakers.mine.bukkit.ChatUtils;
import com.elmakers.mine.bukkit.utility.help.Help;
import com.elmakers.mine.bukkit.utility.help.HelpTopic;
import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;
import com.google.common.collect.ImmutableSet;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;

public class DiscordChatListener extends ListenerAdapter {
    private static final int MAX_BUTTONS = 5 * 5;
    private static final int MAX_BUTTONS_PER_ROW = 5;
    private static final Pattern MHELP_PATTERN = Pattern.compile("/mhelp ([a-z_A-Z\\.]*)");
    private final MagicDiscordHelpPlugin controller;
    private final Help help;
    private final Set<Long> sentLanguage = new HashSet<>();
    private final Set<String> languageKeywords = ImmutableSet.of("language", "translat", "localiz");

    public DiscordChatListener(MagicDiscordHelpPlugin controller) {
        this.controller = controller;
        help = controller.getMagic().getMessages().getHelp();
    }

    protected List<Button> processButtons(Member member, List<Button> buttons) {
        int maxButtons = MAX_BUTTONS;
        Button joinButton = getJoinButton(member);
        if (joinButton != null) {
            maxButtons--;
        }
        if (buttons.size() > maxButtons) {
            buttons = buttons.subList(0, maxButtons);
        }
        if (joinButton != null) {
            buttons.add(joinButton);
        }
        return buttons;
    }

    protected List<ActionRow> getActionRows(List<Button> buttons) {
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += MAX_BUTTONS_PER_ROW) {
            int last = Math.min(buttons.size(), i + MAX_BUTTONS_PER_ROW);
            ActionRow row = ActionRow.of(buttons.subList(i, last));
            rows.add(row);
        }
        return rows;
    }

    protected void addButtons(Member member, ReplyAction action, List<Button> buttons) {
        if (buttons != null && !buttons.isEmpty()) {
            buttons = processButtons(member, buttons);
            action.addActionRows(getActionRows(buttons));
        }
    }

    protected void addButtons(Member member, MessageAction action, List<Button> buttons) {
        if (buttons != null && !buttons.isEmpty()) {
            buttons = processButtons(member, buttons);
            action.setActionRows(getActionRows(buttons));
        }
    }

    protected void respond(Message originalMessage, String message, List<Button> buttons) {
        responded(originalMessage);
        MessageAction action = originalMessage.reply(message);
        addButtons(originalMessage.getMember(), action, buttons);
        action.queue(sentMessage -> sentMessage.suppressEmbeds(true).queue(), throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in channel " + originalMessage.getChannel(), throwable));
    }

    protected void getTopicButtons(String message, List<Button> buttons) {
        Matcher m = MHELP_PATTERN.matcher(message);
        while (m.find() && buttons.size() < MAX_BUTTONS) {
            String topicKey = m.group(1);
            HelpTopic topic = help.getTopic(topicKey);
            if (topic == null) continue;
            Button button = Button.primary("help:" + topicKey, topic.getTitle());
            buttons.add(button);
        }
    }

    protected void responded(Message authorMessage) {
        String responseReaction = controller.getReactionEmote();
        if (!responseReaction.isEmpty() && !authorMessage.getFlags().contains(Message.MessageFlag.EPHEMERAL)) {
            authorMessage.addReaction(responseReaction).queue();
        }
    }

    protected String getTopicMessage(HelpTopic topic, List<Button> buttons) {
        String topicText = topic.getText();
        topicText = getSimpleMessage(topicText);
        getTopicButtons(topicText, buttons);
        // Have to do this after getTopicButtons, the escaping of _ will mess with it
        topicText = translateMessage(topicText);
        return topicText;
    }

    protected String getSimpleMessage(String message) {
        // Do this in two steps to avoid escaping **, kinda hacky yeah
        message = ChatUtils.getSimpleMessage(message, false, " <b>", "<b>");
        message = message.replace("*", "\\*");
        message = message.replace("<b>", "**");
        return message;
    }

    protected String translateMessage(String message) {
        // Oh yeah hacks sue me
        message = message.replace("➽", "");
        message = message.replace("_", "\\_");
        if (message.length() >= 2000) {
            message = message.substring(0, 1996) + "...";
        }
        return ChatColor.stripColor(message);
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        MessageChannel channel = event.getChannel();
        if (channel.getName().equals(controller.getIgnoreChannel())) return;
        String reactionChanel = controller.getReactionChannel();
        String targetChannel = controller.getChannel();
        if (!reactionChanel.equals("*") && !channel.getName().equals(reactionChanel) && !channel.getName().equals(targetChannel)) return;

        Button button = event.getButton();
        if (button == null) return;
        String id = button.getId();
        if (id == null) return;
        if (id.startsWith("help:")) {
            final String topicId = id.substring(5);
            HelpTopic topic = help.getTopic(topicId);
            Message originalMessage = event.getMessage();
            responded(originalMessage);
            if (topic != null) {
                List<Button> buttons = new ArrayList<>();
                String message = getTopicMessage(topic, buttons);
                ReplyAction action = event.reply(message);
                action.setEphemeral(true);
                addButtons(event.getMember(), action, buttons);
                action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + id, throwable));
            } else {
                event.reply("Could not find help topic: " + id).queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click: " + topicId, throwable));
            }
        }
        if (id.startsWith("verify:")) {
            final String memberId = id.substring(7);
            Member member = event.getMember();
            if (member == null) return;

            Guild guild = event.getGuild();
            Role role = getJoinRole(guild);
            if (role == null) {
                controller.getLogger().warning("Getting verification button clicks without a join role");
                return;
            }

            if (!member.getRoles().contains(role)) {
                ReplyAction action = event.reply(controller.getMagic().getMessages().get("discord.already_verified"));
                action.setEphemeral(true);
                action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + id, throwable));
                return;
            }

            if (!member.getId().equals(memberId)) {
                ReplyAction action = event.reply(controller.getMagic().getMessages().get("discord.verified_invalid"));
                action.setEphemeral(true);
                action.addActionRow(getVerifyButton(member));
                action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + id, throwable));
                return;
            }

            guild.removeRoleFromMember(member, role).queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to remove role from user " + member.getEffectiveName(), throwable));
            ReplyAction action = event.reply(controller.getMagic().getMessages().get("discord.verified"));
            action.setEphemeral(true);
            action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + id, throwable));

            // Remove the join button when not on an ephemeral messages
            Message clickedMessage = event.getMessage();
            if (!clickedMessage.getFlags().contains(Message.MessageFlag.EPHEMERAL)) {
                List<ActionRow> actionRows = clickedMessage.getActionRows();
                if (!actionRows.isEmpty()) {
                    List<Component> keepButtons = new ArrayList<>();
                    for (ActionRow row : actionRows) {
                        for (Component component : row.getComponents()) {
                            if (!component.equals(button)) {
                                keepButtons.add(component);
                            }
                        }
                    }
                    MessageAction editMessage;
                    if (keepButtons.isEmpty()) {
                        editMessage = clickedMessage.editMessageComponents();
                    } else {
                        editMessage = clickedMessage.editMessageComponents(ActionRow.of(keepButtons));
                    }
                    editMessage.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to remove join button from message " + id, throwable));
                }
            }
        }
        if (id.startsWith("next:")) {
            String remainder = id.substring(5);
            String[] pieces = StringUtils.split(remainder, ",");
            int next = 1;
            String nextString = pieces[0].trim();
            if (!nextString.isEmpty()) {
                try {
                    next = Integer.parseInt(nextString);
                } catch (Exception ex) {
                    controller.getLogger().warning("Invalid next index: " + remainder);
                }
            }
            String originalMessageId = pieces.length > 1 ? pieces[1] : null;
            Message clickedMessage = event.getMessage();
            Message originalMessage;
            if (originalMessageId == null) {
                originalMessage = getOriginal(clickedMessage);
                showNextTopic(originalMessage, next, event);
            } else {
                final int nextIndex = next;
                event.getChannel().retrieveMessageById(originalMessageId).queue(
                    foundMessage -> showNextTopic(foundMessage, nextIndex, event),
                    throwable -> controller.getLogger().log(Level.SEVERE, "Error retrieving original message from click " + originalMessageId
                ));
            }
        }
    }

    private void showNextTopic(Message originalMessage, int next, ButtonClickEvent event) {
        if (originalMessage == null) {
            controller.getLogger().warning("Could not find original message from button click");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String msg = originalMessage.getContentDisplay();
        String response = getResponse(msg, buttons, originalMessage.getId(), next);
        ReplyAction action = event.reply(response);
        addButtons(event.getMember(), action, buttons);
        action.setEphemeral(true);
        action.queue(sentMessage -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send message in response to button click " + event.getButton().getId(), throwable));
    }

    private Message getOriginal(Message message) {
        MessageReference replied = message.getMessageReference();
        while (replied != null) {
            Message repliedMessage = replied.getMessage();
            if (repliedMessage == null) break;
            message = repliedMessage;
            replied = message.getMessageReference();
        }
        return message;
    }

    private Button getJoinButton(Member member) {
        Button verifyButton = null;
        Role joinRole = getJoinRole(member.getGuild());
        if (joinRole != null) {
            if (member != null && member.getRoles().contains(joinRole)) {
                verifyButton = getVerifyButton(member);
            }
        }
        return verifyButton;
    }

    private Role getJoinRole(Guild guild) {
        String joinRole = controller.getJoinRole();
        if (joinRole.isEmpty()) return null;
        Role role = guild.getRoleById(joinRole);
        if (role == null) {
            controller.getLogger().warning("Invalid join role id: " + joinRole);
        }
        return role;
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        User user = event.getUser();
        if (user != null && user.isBot()) return;
        MessageChannel channel = event.getChannel();
        if (channel.getName().equals(controller.getIgnoreChannel())) return;
        String reactionChanel = controller.getReactionChannel();
        if (!reactionChanel.equals("*") && !channel.getName().equals(reactionChanel)) return;
        String reactionCode = event.getReaction().getReactionEmote().getAsReactionCode();
        if (!reactionCode.equals(controller.getReactionEmote())) return;
        event.retrieveMessage().queue(this::checkReactionAdd);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Role role = getJoinRole(guild);
        if (role == null) {
            return;
        }

        Member member = event.getMember();
        guild.addRoleToMember(member, role).queue(success -> controller.getLogger().info("Assigned join role to " + member.getEffectiveName()), throwable -> controller.getLogger().log(Level.SEVERE, "Failed to assign role to " + member.getEffectiveName(), throwable));

        String channelName = controller.getWelcomeChannel();
        List<TextChannel> helpChannels = guild.getTextChannelsByName(channelName, true);
        if (!helpChannels.isEmpty()) {
            MessageChannel helpChannel = helpChannels.get(0);
            String welcomeMessage = controller.getMagic().getMessages().get("discord.welcome");
            welcomeMessage = welcomeMessage.replace("$member", member.getAsMention());
            MessageAction welcomeAction = helpChannel.sendMessage(welcomeMessage);
            welcomeAction.setActionRow(getFirstVerifyButton(member));
            welcomeAction.queue(success -> {}, throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send welcome message to " + member.getEffectiveName(), throwable));
        }
    }

    protected Button getFirstVerifyButton(Member member) {
        return Button.success("verify:" + member.getId(), controller.getMagic().getMessages().get("discord.verify_first_button"));
    }

    protected Button getVerifyButton(Member member) {
        return Button.success("verify:" + member.getId(), controller.getMagic().getMessages().get("discord.verify_button"));
    }

    protected void checkReactionAdd(Message message) {
        String emote = controller.getReactionEmote();
        List<MessageReaction> reactions = message.getReactions();
        for (MessageReaction reaction : reactions) {
            if (reaction.getReactionEmote().getAsReactionCode().equals(emote) && reaction.getCount() > 1) {
                return;
            }
        }
        respondToMessage(message);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot()) return;
        MessageChannel channel = event.getChannel();
        if (channel.getName().equals(controller.getIgnoreChannel())) return;

        Message message = event.getMessage();
        String channelResponse = controller.getChannelResponse(channel.getName());
        channelResponse = controller.getMagic().getMessages().get(channelResponse, channelResponse);
        if (channelResponse != null && !channelResponse.isEmpty()) {
            respond(message, channelResponse, null);
            return;
        }

        List<Member> members = message.getMentionedMembers();

        boolean mentioned = false;
        String mentionChannel = controller.getMentionChannel();
        String mentionId = controller.getMentionId();
        List<Member> mention = new ArrayList<>();

        for (Member member : members) {
            if (member.getId().equals(mentionId)) {
                mentioned = true;
            } else {
                mention.add(member);
            }
        }

        if (!mentionChannel.equals("*") && !mentionChannel.equals(channel.getName())) {
            mentioned = false;
        }

        // Only listen to a specific channel, unless mentioned
        if (!mentioned && !channel.getName().equals(controller.getChannel())) return;

        // Ignore replies, unless mentioning
        if (!mentioned && message.getMessageReference() != null) return;
        respondToMessage(message, mention);
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        MessageChannel channel = event.getChannel();
        String channelName = channel.getName();
        if (channelName.equals(controller.getIgnoreChannel())) return;
        String commandChannel = controller.getCommandChannel();
        if (!commandChannel.equals("*") && !channelName.equals(commandChannel)) return;

        String command = controller.getCommand();
        if (!event.getName().equals(command)) return;
        String topic = event.getOption("topic").getAsString();

        List<Button> buttons = new ArrayList<>();
        String response = getResponse(topic, buttons);
        ReplyAction reply = event.reply(response).setEphemeral(true);
        addButtons(event.getMember(), reply, buttons);
        reply.queue();
    }

    public void registerCommands(Guild guild) {
        final String command = controller.getCommand();
        if (command.isEmpty()) return;

        CommandListUpdateAction commands = guild.updateCommands();
        commands.addCommands(
                new CommandData(command, "Show a specific help topic")
                        .addOptions(new OptionData(OptionType.STRING, "topic", "The topic to show").setRequired(true))
        );
        commands.queue(success -> controller.getLogger().info("Registered /" + command + " command"), throwable -> controller.getLogger().log(Level.WARNING, "Could not register slash commands", throwable));
    }

    protected void sendTranslateMessage(Message message) {
        String translateMessage = controller.getMagic().getMessages().get("discord.language");
        MessageAction response = message.reply(translateMessage);
        response.queue(sentMessage -> sentMessage.addReaction("🇺🇸").queue(), throwable -> controller.getLogger().log(Level.SEVERE, "Failed to send language message response", throwable));
    }

    protected String getResponse(String msg, List<Button> buttons) {
        return getResponse(msg, buttons, false, null, 0);
    }

    protected String getResponse(String msg, List<Button> buttons, String originalMessageId, int startingAt) {
        return getResponse(msg, buttons, false, originalMessageId, startingAt);
    }

    protected String getTopResponse(String msg, List<Button> buttons, String originalMessageId) {
        return getResponse(msg, buttons, true, originalMessageId, 0);
    }

    private String getResponse(String msg, List<Button> buttons, boolean topOnly, String originalMessageId, int startingAt) {
        String[] pieces = ChatUtils.getWords(msg.toLowerCase());
        if (pieces.length == 0) {
            return controller.getMagic().getMessages().get("discord.empty");
        }

        if (pieces.length == 1) {
            HelpTopic topic = help.getTopic(pieces[0]);
            if (topic != null) {
                return getTopicMessage(topic, buttons);
            }
        }
        List<String> keywords = Arrays.asList(pieces);
        List<HelpTopicMatch> matches = help.findMatches(keywords, MAX_BUTTONS_PER_ROW);
        int removed = 0;
        while (!matches.isEmpty() && removed < startingAt) {
            matches.remove(0);
            removed++;
        }
        if (matches.isEmpty()) {
            return controller.getMagic().getMessages().get("discord.not_found");
        }
        if (matches.size() == 1) {
            return getTopicMessage(matches.get(0).getTopic(), buttons);
        }
        if (topOnly && originalMessageId != null) {
            HelpTopic topic = matches.get(0).getTopic();
            String message = getTopicMessage(topic, buttons);
            Button showAllButton = Button.success("next:1" + "," + originalMessageId, "Other Answers");
            buttons.add(0, showAllButton);
            return message;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int maxButtons = MAX_BUTTONS_PER_ROW;
        boolean showOther = startingAt > 0 && matches.size() > startingAt + MAX_BUTTONS_PER_ROW - 1 && originalMessageId != null;
        if (showOther) maxButtons = MAX_BUTTONS_PER_ROW - 1;
        for (HelpTopicMatch match : matches) {
            if (count++ >= maxButtons) break;
            String title = match.getTopic().getTitle();
            String summary = match.getSummary(100, "\uFEFF**", "**\uFEFF");
            sb.append("\n");
            if (controller.isDebug()) {
                int relevance = (int)(100.0 * match.getRelevance());
                sb.append(relevance + ": ");
            }
            sb.append(title);
            sb.append(" : ");
            sb.append(summary);
            Button button = Button.primary("help:" + match.getTopic().getKey(), title);
            buttons.add(button);
        }

        if (showOther) {
            int nextStart = startingAt + MAX_BUTTONS_PER_ROW - 1;
            Button showAllButton = Button.success("next:" + nextStart + "," + originalMessageId, "Other Answers");
            buttons.add(0, showAllButton);
        }

        String searchResults = sb.toString();
        searchResults = translateMessage(searchResults);
        return searchResults;
    }

    protected void respondToMessage(Message message) {
        respondToMessage(message, null);
    }

    protected void respondToMessage(Message message, List<Member> mentions) {
        String msg = message.getContentDisplay();
        User author = message.getAuthor();
        if (!sentLanguage.contains(author.getIdLong())) {
            String lowerMessage = msg.toLowerCase();
            for (String keyword : languageKeywords) {
                if (lowerMessage.contains(keyword)) {
                    sentLanguage.add(author.getIdLong());
                    sendTranslateMessage(message);
                    break;
                }
            }
        }

        List<Button> buttons = new ArrayList<>();
        String response = getTopResponse(msg, buttons, message.getId());
        if (mentions != null && !mentions.isEmpty()) {
            List<String> mentionStrings = new ArrayList<>();
            for (Member member : mentions) {
                mentionStrings.add(member.getAsMention());
            }
            String mention = controller.getMagic().getMessages().get("discord.showing");
            mention = mention.replace("$mentions", StringUtils.join(mentionStrings, " "));
            response = mention + "\n" + response;
        }
        respond(message, response, buttons);
    }
}
