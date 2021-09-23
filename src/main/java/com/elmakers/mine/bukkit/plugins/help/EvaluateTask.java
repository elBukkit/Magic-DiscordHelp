package com.elmakers.mine.bukkit.plugins.help;

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

    // min, max, step
    public static final double[][] SEARCH_SPACES = {
        {0.1, 1.0, 0.1},
        {1.5, 5.0, 0.5},
        {5.1, 6.0, 0.1}
    };
    private final Map<String, EvaluationProperty> properties = new HashMap<>();

    private static final Map<String, Evaluation> evaluations = new HashMap<>();

    // Instance
    private final CommandSender sender;
    private final ConfigurationSection goals;
    private final Plugin plugin;
    private final MagicController magic;
    private final Map<String, List<Evaluation>> results = new HashMap<>();
    private final int repeat;

    public EvaluateTask(CommandSender sender, Plugin plugin, MagicController magic, ConfigurationSection goals, int repeat) {
        this.sender = sender;
        this.goals = goals;
        this.plugin = plugin;
        this.magic = magic;
        this.repeat = repeat;

        EvaluationProperty.register(properties, "COUNT_FACTOR", HelpTopicKeywordMatch.class, HelpTopicKeywordMatch.COUNT_FACTOR);
        EvaluationProperty.register(properties, "WORD_FACTOR", HelpTopicKeywordMatch.class, HelpTopicKeywordMatch.WORD_FACTOR);
        EvaluationProperty.register(properties, "SIMILARITY_FACTOR", HelpTopicKeywordMatch.class, HelpTopicKeywordMatch.SIMILARITY_FACTOR);
        EvaluationProperty.register(properties, "COUNT_WEIGHT", HelpTopicKeywordMatch.class, HelpTopicKeywordMatch.COUNT_WEIGHT);
        EvaluationProperty.register(properties, "WORD_WEIGHT", HelpTopicKeywordMatch.class, HelpTopicKeywordMatch.COUNT_WEIGHT);
        EvaluationProperty.register(properties, "CONTENT_FACTOR", HelpTopicMatch.class, HelpTopicMatch.CONTENT_FACTOR);
        EvaluationProperty.register(properties, "TAG_FACTOR", HelpTopicMatch.class, HelpTopicMatch.TAG_FACTOR);
        EvaluationProperty.register(properties, "TITLE_FACTOR", HelpTopicMatch.class, HelpTopicMatch.TITLE_FACTOR);
        EvaluationProperty.register(properties, "CONTENT_WEIGHT", HelpTopicMatch.class, HelpTopicMatch.CONTENT_WEIGHT);
        EvaluationProperty.register(properties, "TAG_WEIGHT", HelpTopicMatch.class, HelpTopicMatch.TAG_WEIGHT);
        EvaluationProperty.register(properties, "TITLE_WEIGHT", HelpTopicMatch.class, HelpTopicMatch.TITLE_WEIGHT);
        EvaluationProperty.register(properties, "RARITY_FACTOR", HelpTopicWord.class, HelpTopicWord.RARITY_FACTOR);
        EvaluationProperty.register(properties, "TOPIC_RARITY_FACTOR", HelpTopicWord.class, HelpTopicWord.TOPIC_RARITY_FACTOR);
        EvaluationProperty.register(properties, "LENGTH_FACTOR", HelpTopicWord.class, HelpTopicWord.LENGTH_FACTOR);
        EvaluationProperty.register(properties, "RARITY_WEIGHT", HelpTopicWord.class, HelpTopicWord.RARITY_WEIGHT);
        EvaluationProperty.register(properties, "TOPIC_RARITY_WEIGHT", HelpTopicWord.class, HelpTopicWord.TOPIC_RARITY_WEIGHT);
        EvaluationProperty.register(properties, "LENGTH_WEIGHT", HelpTopicWord.class, HelpTopicWord.LENGTH_WEIGHT);
    }

    @Override
    public void run() {
        try {
            if (repeat <= 0) {
                runSingleEvaluation(sender);
                return;
            }
            Map<String, Map<Double, Integer>> valueCounts = new HashMap<>();
            Map<String, Integer> bestCounts = new HashMap<>();
            Map<String, Double> bestValues = new HashMap<>();
            for (int i = 0; i < repeat; i++) {
                runEvaluation(sender);
                if (i < repeat - 1) {
                    int remaining = repeat - i;
                    sender.sendMessage(ChatColor.YELLOW + "Remaining runs: "+ ChatColor.GOLD + remaining);
                }

                for (Map.Entry<String, List<Evaluation>> entry : results.entrySet()) {
                    double value = entry.getValue().get(0).getValue();
                    String property = entry.getKey();
                    Map<Double, Integer> valueCountMap = valueCounts.get(property);
                    if (valueCountMap == null) {
                        valueCountMap = new HashMap<>();
                    }
                    Integer valueCount = valueCountMap.get(value);
                    if (valueCount == null) {
                        valueCount = 1;
                    } else {
                        valueCount++;
                    }
                    valueCountMap.put(value, valueCount);
                    Integer bestCount = bestCounts.get(property);
                    if (bestCount == null || valueCount > bestCount) {
                        bestCounts.put(property, valueCount);
                        bestValues.put(property, value);
                    }
                }

                results.clear();
            }
            if (repeat <= 1) {
                sender.sendMessage(ChatColor.GOLD + " Finished.");
            } else {
                sender.sendMessage(ChatColor.GOLD + " Finished, applying most common selections: ");
                for (Map.Entry<String, Double> entry : bestValues.entrySet()) {
                    double value = entry.getValue();
                    EvaluationProperty evaluationProperty = properties.get(entry.getKey());
                    evaluationProperty.setDefaultValue(value);
                    sender.sendMessage(evaluationProperty.getDescription() + ChatColor.GRAY + " = " + ChatColor.GREEN + value);
                }
            }
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Something went wrong!");
            plugin.getLogger().log(Level.SEVERE, "Error loading evaluation goals", ex);
        }
    }

    private void runSingleEvaluation(CommandSender sender) throws NoSuchFieldException, IllegalAccessException {
        sender.sendMessage("Checking current default values:");
        for (EvaluationProperty property : properties.values()) {
            sender.sendMessage(ChatColor.DARK_AQUA + " " + ChatColor.AQUA + property.getDescription()
                    + ChatColor.DARK_AQUA + " = " + property.getDefaultValue());
            property.restoreDefaultValue();
        }
        Evaluation evaluation = evaluate(goals, 0);
        sender.sendMessage("Score: " + ChatColor.GREEN + evaluation.getRatio());
        Set<String> missing = evaluation.getMissingTopics();
        if (!missing.isEmpty()) {
            sender.sendMessage("Missing: " + StringUtils.join(missing, " | "));
        }
    }

    private void runEvaluation(CommandSender sender) throws NoSuchFieldException, IllegalAccessException {
        for (EvaluationProperty property : properties.values()) {
            runEvaluation(sender, goals, property);
        }
        sender.sendMessage("Applying recommended changes: ");
        for (Map.Entry<String, List<Evaluation>> entry : results.entrySet()) {
            Evaluation top = entry.getValue().get(0);
            EvaluationProperty property = properties.get(entry.getKey());
            if (top.hasMissingTopics()) {
                sender.sendMessage(ChatColor.RED + property.getDescription() + ChatColor.GRAY + " : " + ChatColor.GOLD + "skipped due to missing topics");
                continue;
            }
            double value = top.getValue();
            sender.sendMessage(property.getDescription() + ChatColor.GRAY + " = " + ChatColor.GREEN + value);
            property.setDefaultValue(value);
        }
    }

    private void runEvaluation(CommandSender sender, ConfigurationSection goals, EvaluationProperty property)
            throws NoSuchFieldException, IllegalAccessException {
        String propertyName = property.getProperty();
        List<Double> values = new ArrayList<>();
        for (double[] searchSpace : SEARCH_SPACES) {
            int i = 0;
            double value = searchSpace[0] + searchSpace[2] * i;
            while (value <= searchSpace[1]) {
                values.add(value);
                i++;
                value = searchSpace[0] + searchSpace[2] * i;
            }
        }
        values.add(property.getDefaultValue());
        Collections.sort(values);
        sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + ChatColor.AQUA + propertyName
            + ChatColor.DARK_AQUA + " from " + ChatColor.GREEN + values.get(0)
            + ChatColor.DARK_AQUA + " to " + ChatColor.GREEN + values.get(values.size() - 1));
        List<Evaluation> evaluations = new ArrayList<>();
        for (double value : values) {
            property.set(value);
            evaluations.add(evaluate(goals, value));
        }
        property.restoreDefaultValue();
        showEvaluation(sender, evaluations);
        sender.sendMessage("");
        Map<String, Integer> missing = new HashMap<>();
        for (Evaluation evaluation : evaluations) {
            evaluation.addMissingTopics(missing);
        }
        if (!missing.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                messages.add(entry.getKey() + ": " + ChatUtils.printPercentage((double)entry.getValue() / evaluations.size()));
            }
            sender.sendMessage("Missing: " + StringUtils.join(messages, " | "));
        } else {
            results.put(propertyName, evaluations);
        }
    }

    private void showEvaluation(CommandSender sender, List<Evaluation> evaluations) {
        Collections.sort(evaluations);
        String headerRow = "";
        String valueRow = "";
        final int NUMERIC_WIDTH = 6;
        for (Evaluation evaluation : evaluations) {
            ChatColor color = evaluation.hasMissingTopics() ? ChatColor.RED : ChatColor.AQUA;
            headerRow += color + ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getValue()), NUMERIC_WIDTH);
            color = evaluation.hasMissingTopics() ? ChatColor.RED : ChatColor.GREEN;
            valueRow += color + ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getRatio()), NUMERIC_WIDTH);
        }
        sender.sendMessage(headerRow);
        int totalWidth = NUMERIC_WIDTH * evaluations.size();
        sender.sendMessage(StringUtils.repeat("_", totalWidth));
        sender.sendMessage(valueRow);
    }

    private Evaluation evaluate(ConfigurationSection goals, double value) throws NoSuchFieldException, IllegalAccessException {
        Set<String> goalKeys = goals.getKeys(true);
        Help help = magic.getMessages().getHelp();
        help.resetStats();

        String key = getEvaluationKey();
        Evaluation existing = evaluations.get(key);
        if (existing != null) {
            return new Evaluation(existing, value);
        }

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
        evaluations.put(key, evaluation);
        return evaluation;
    }

    private String getEvaluationKey() throws NoSuchFieldException, IllegalAccessException {
        String key = "";
        for (EvaluationProperty property : properties.values()) {
            key += "|" + property.get();
        }
        return key;
    }
}
