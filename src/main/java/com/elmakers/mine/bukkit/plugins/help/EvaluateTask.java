package com.elmakers.mine.bukkit.plugins.help;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
    public static final double[] SIMILARITY_FACTOR = {0.2, 5.0, 0.2, HelpTopicKeywordMatch.SIMILARITY_FACTOR};
    public static final double[] COUNT_WEIGHT = {0.5, 5, 0.5, HelpTopicKeywordMatch.COUNT_WEIGHT};
    public static final double[] WORD_WEIGHT = {0.5, 5, 0.5, HelpTopicKeywordMatch.COUNT_WEIGHT};

    // HelpTopicMatch
    public static final double[] CONTENT_FACTOR = {0.2, 2.0, 0.2, HelpTopicMatch.CONTENT_FACTOR};
    public static final double[] TAG_FACTOR = {0.2, 2.0, 0.2, HelpTopicMatch.TAG_FACTOR};
    public static final double[] TITLE_FACTOR = {0.2, 2.0, 0.2, HelpTopicMatch.TITLE_FACTOR};

    // HelpTopicWord
    private static final double[] RARITY_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.RARITY_FACTOR};
    private static final double[] TOPIC_RARITY_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.TOPIC_RARITY_FACTOR};
    private static final double[] LENGTH_FACTOR = {0.2, 2.0, 0.2, HelpTopicWord.LENGTH_FACTOR};

    private static final double[] RARITY_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.RARITY_WEIGHT};
    private static final double[] TOPIC_RARITY_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.TOPIC_RARITY_WEIGHT};
    private static final double[] LENGTH_WEIGHT = {0.2, 2.0, 0.2, HelpTopicWord.LENGTH_WEIGHT};

    // A Guava immutable map would work better here but whatever this is not production code
    private final Map<String, Class<?>> propertyClasses = new HashMap<>();

    // Instance
    private final CommandSender sender;
    private final ConfigurationSection goals;
    private final Plugin plugin;
    private final MagicController magic;
    private final Map<String, List<Evaluation>> results = new HashMap<>();

    public EvaluateTask(CommandSender sender, Plugin plugin, MagicController magic, ConfigurationSection goals) {
        this.sender = sender;
        this.goals = goals;
        this.plugin = plugin;
        this.magic = magic;

        // See above note about how this should be static and initialized with a builder

        propertyClasses.put("COUNT_FACTOR", HelpTopicKeywordMatch.class);
        propertyClasses.put("WORD_FACTOR", HelpTopicKeywordMatch.class);
        propertyClasses.put("SIMILARITY_FACTOR", HelpTopicKeywordMatch.class);
        propertyClasses.put("COUNT_WEIGHT", HelpTopicKeywordMatch.class);
        propertyClasses.put("WORD_WEIGHT", HelpTopicKeywordMatch.class);
        propertyClasses.put("CONTENT_FACTOR", HelpTopicMatch.class);
        propertyClasses.put("TAG_FACTOR", HelpTopicMatch.class);
        propertyClasses.put("TITLE_FACTOR", HelpTopicMatch.class);
        propertyClasses.put("RARITY_FACTOR", HelpTopicWord.class);
        propertyClasses.put("TOPIC_RARITY_FACTOR", HelpTopicWord.class);
        propertyClasses.put("LENGTH_FACTOR", HelpTopicWord.class);
        propertyClasses.put("RARITY_WEIGHT", HelpTopicWord.class);
        propertyClasses.put("TOPIC_RARITY_WEIGHT", HelpTopicWord.class);
        propertyClasses.put("LENGTH_WEIGHT", HelpTopicWord.class);
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

    private void runEvaluation(CommandSender sender) throws NoSuchFieldException, IllegalAccessException {
        runEvaluation(sender, goals, COUNT_FACTOR, "COUNT_FACTOR");
        runEvaluation(sender, goals, WORD_FACTOR, "WORD_FACTOR");
        runEvaluation(sender, goals, SIMILARITY_FACTOR, "SIMILARITY_FACTOR");
        runEvaluation(sender, goals, COUNT_WEIGHT, "COUNT_WEIGHT");
        runEvaluation(sender, goals, WORD_WEIGHT, "WORD_WEIGHT");
        runEvaluation(sender, goals, CONTENT_FACTOR, "CONTENT_FACTOR");
        runEvaluation(sender, goals, TAG_FACTOR, "TAG_FACTOR");
        runEvaluation(sender, goals, TITLE_FACTOR, "TITLE_FACTOR");
        runEvaluation(sender, goals, RARITY_FACTOR, "RARITY_FACTOR");
        runEvaluation(sender, goals, TOPIC_RARITY_FACTOR, "TOPIC_RARITY_FACTOR");
        runEvaluation(sender, goals, LENGTH_FACTOR, "LENGTH_FACTOR");
        runEvaluation(sender, goals, RARITY_WEIGHT, "RARITY_WEIGHT");
        runEvaluation(sender, goals, TOPIC_RARITY_WEIGHT, "TOPIC_RARITY_WEIGHT");
        runEvaluation(sender, goals, LENGTH_WEIGHT, "LENGTH_WEIGHT");
        sender.sendMessage("Applying recommended changes: ");
        for (Map.Entry<String, List<Evaluation>> entry : results.entrySet()) {
            double value = entry.getValue().get(0).getValue();
            String property = entry.getKey();
            sender.sendMessage(ChatColor.AQUA + "  " + property + ChatColor.GRAY + " = " + ChatColor.GREEN + value);
            Class<?> propertyClass = propertyClasses.get(property);
            Field valueField = propertyClass.getField(property);
            valueField.set(null, value);
        }
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
        }
        return evaluation;
    }

    private void runEvaluation(CommandSender sender, ConfigurationSection goals, double[] values, String propertyName)
            throws NoSuchFieldException, IllegalAccessException {
        sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + ChatColor.AQUA + propertyName
            + ChatColor.DARK_AQUA + " from " + ChatColor.GREEN + values[0]
            + ChatColor.DARK_AQUA + " to " + ChatColor.GREEN + values[1]);
        List<Evaluation> evaluations = new ArrayList<>();
        Class<?> propertyClass = propertyClasses.get(propertyName);
        Field valueField = propertyClass.getField(propertyName);
        for (double value = values[0]; value <= values[1]; value += values[2]) {
            valueField.set(null, value);
            evaluations.add(evaluate(sender, goals, value));
        }
        valueField.set(null, values[3]);
        showEvaluation(sender, evaluations);
        sender.sendMessage("");
        Map<String, Integer> missing = new HashMap<>();
        for (Evaluation evaluation : evaluations) {
            evaluation.getMissingTopics(missing);
        }
        if (!missing.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                messages.add(entry.getKey() + ": " + ChatUtils.printPercentage((double)entry.getValue() / evaluations.size()));
            }
            sender.sendMessage("Missing: " + StringUtils.join(messages, " | "));
        }
        results.put(propertyName, evaluations);
    }
}
