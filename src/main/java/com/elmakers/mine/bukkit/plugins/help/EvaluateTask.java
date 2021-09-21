package com.elmakers.mine.bukkit.plugins.help;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.ChatUtils;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.utility.help.Help;
import com.elmakers.mine.bukkit.utility.help.HelpTopicKeywordMatch;
import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;
import com.elmakers.mine.bukkit.utility.help.HelpTopicWord;

public class EvaluateTask implements Runnable {
    private static final String NUMERIC_FORMAT = "%.1f";

    // min, max, step, default

    // HelpTopicKeywordMatch
    public static final double[] COUNT_FACTOR = {0.2, 2.0, 0.2, HelpTopicKeywordMatch.COUNT_FACTOR};
    public static final double[] WORD_FACTOR = {0.2, 2.0, 0.2, HelpTopicKeywordMatch.WORD_FACTOR};
    public static final double[] COUNT_WEIGHT = {0.5, 5, 0.5, HelpTopicKeywordMatch.COUNT_WEIGHT};
    public static final double[] WORD_WEIGHT = {0.5, 5, 0.5, HelpTopicKeywordMatch.COUNT_WEIGHT};

    // HelpTopicMatch
    public static final double[] CONTENT_WEIGHT = {0.5, 5, 0.5, HelpTopicMatch.CONTENT_WEIGHT};
    public static final double[] TAG_WEIGHT = {0.5, 5, 0.5, HelpTopicMatch.TAG_WEIGHT};
    public static final double[] TITLE_WEIGHT = {0.5, 5, 0.5, HelpTopicMatch.TITLE_WEIGHT};

    // HelpTopicWord
    private static final double[] RARITY_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.RARITY_FACTOR};
    private static final double[] TOPIC_RARITY_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.TOPIC_RARITY_FACTOR};
    private static final double[] LENGTH_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.LENGTH_FACTOR};

    private static final double[] RARITY_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.RARITY_WEIGHT};
    private static final double[] TOPIC_RARITY_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.TOPIC_RARITY_WEIGHT};
    private static final double[] LENGTH_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.LENGTH_WEIGHT};

    // Instance
    private final CommandSender sender;
    private final ConfigurationSection goals;
    private final Plugin plugin;
    private final MagicController magic;

    public EvaluateTask(CommandSender sender, Plugin plugin, MagicController magic, ConfigurationSection goals) {
        this.sender = sender;
        this.goals = goals;
        this.plugin = plugin;
        this.magic = magic;
    }

    @Override
    public void run() {
        try {
            runEvaluation(sender);
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Something went wrong!");
            plugin.getLogger().log(Level.SEVERE, "Error loading evaluation goals", ex);
        }
    }

    private void runEvaluation(CommandSender sender)
            throws IOException, InvalidConfigurationException, NoSuchFieldException, IllegalAccessException {
        runEvaluation(sender, goals, COUNT_FACTOR, "COUNT_FACTOR", HelpTopicKeywordMatch.class);
        runEvaluation(sender, goals, WORD_FACTOR, "WORD_FACTOR", HelpTopicKeywordMatch.class);
        runEvaluation(sender, goals, COUNT_WEIGHT, "COUNT_WEIGHT", HelpTopicKeywordMatch.class);
        runEvaluation(sender, goals, CONTENT_WEIGHT, "CONTENT_WEIGHT", HelpTopicMatch.class);
        runEvaluation(sender, goals, TAG_WEIGHT, "TAG_WEIGHT", HelpTopicMatch.class);
        runEvaluation(sender, goals, TITLE_WEIGHT, "TITLE_WEIGHT", HelpTopicMatch.class);
        runEvaluation(sender, goals, RARITY_FACTOR, "RARITY_FACTOR", HelpTopicWord.class);
        runEvaluation(sender, goals, TOPIC_RARITY_FACTOR, "TOPIC_RARITY_FACTOR", HelpTopicWord.class);
        runEvaluation(sender, goals, LENGTH_FACTOR, "LENGTH_FACTOR", HelpTopicWord.class);
        runEvaluation(sender, goals, RARITY_WEIGHT, "RARITY_WEIGHT", HelpTopicWord.class);
        runEvaluation(sender, goals, TOPIC_RARITY_WEIGHT, "TOPIC_RARITY_WEIGHT", HelpTopicWord.class);
        runEvaluation(sender, goals, LENGTH_WEIGHT, "LENGTH_WEIGHT", HelpTopicWord.class);
        sender.sendMessage("... Done ...");
    }

    private void showEvaluation(CommandSender sender, List<Evaluation> evaluations) {
        Collections.sort(evaluations);
        String headerRow = "" + ChatColor.AQUA;
        String valueRow = "" + ChatColor.GREEN;
        final int NUMERIC_WIDTH = 6;
        for (Evaluation evaluation : evaluations) {
            headerRow += ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getValue()), NUMERIC_WIDTH);
            valueRow += ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getRatio()), NUMERIC_WIDTH);
        }
        sender.sendMessage(headerRow);
        int totalWidth = NUMERIC_WIDTH * evaluations.size();
        sender.sendMessage(StringUtils.repeat("_", totalWidth));
        sender.sendMessage(valueRow);

        evaluations.clear();
    }

    private Evaluation evaluate(CommandSender sender, ConfigurationSection goals, double value) {
        Set<String> goalKeys = goals.getKeys(true);
        Help help = magic.getMessages().getHelp();
        help.resetStats();

        Evaluation evaluation = new Evaluation(value);
        for (String goal : goalKeys) {
            List<String> queries = goals.getStringList(goal);
            if (queries == null || queries.isEmpty()) continue;
            evaluation.setGoal(goal);

            for (String query : queries) {
                String[] pieces = ChatUtils.getWords(query.toLowerCase());
                List<String> keywords = Arrays.asList(pieces);
                List<HelpTopicMatch> matches = help.findMatches(keywords, 5);
                evaluation.addResults(matches, goal);
            }

            EvaluationScore goalScore = evaluation.getCurrentGoal();
            if (goalScore.getMatches() == 0) {
                sender.sendMessage("  No matches for: " + goal + " at " + value);
            }
        }
        return evaluation;
    }

    private void runEvaluation(CommandSender sender, ConfigurationSection goals, double[] values, String propertyName, Class<?> propertyClass)
            throws NoSuchFieldException, IllegalAccessException {
        sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + ChatColor.AQUA + propertyName
            + ChatColor.DARK_AQUA + " from " + ChatColor.GREEN + values[0]
            + ChatColor.DARK_AQUA + " to " + ChatColor.GREEN + values[1]);
        List<Evaluation> evaluations = new ArrayList<>();
        Field valueField = propertyClass.getField(propertyName);
        for (double value = values[0]; value <= values[1]; value += values[2]) {
            valueField.set(null, value);
            evaluations.add(evaluate(sender, goals, value));
        }
        valueField.set(null, values[3]);
        showEvaluation(sender, evaluations);
        sender.sendMessage("");
    }
}
