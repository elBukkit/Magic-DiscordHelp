package com.elmakers.mine.bukkit.plugins.help;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.utility.ChatUtils;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.utility.StringUtils;
import com.elmakers.mine.bukkit.utility.help.Help;
import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;
import com.elmakers.mine.bukkit.utility.help.SearchFactors;

public class EvaluateTask implements Runnable {
    private static final String NUMERIC_FORMAT = "%.1f";
    private static final DecimalFormat PRECISE_FORMAT = new DecimalFormat("#.##");
    private static final int NUMERIC_WIDTH = 5;

    // min, max, step
    public static final double[][] WEIGHT_SEARCH_SPACES = {
        {0.1, 1.0, 0.1},
        {1.5, 5.0, 0.5},
        {5.1, 6.0, 0.1}
    };
    public static final double[][] FACTOR_SEARCH_SPACES = {
        {0.1, 1.0, 0.1},
        {1.2, 3.0, 0.2},
        {3.1, 4.0, 0.1}
    };
    public static final double[][] SCALE_SEARCH_SPACES = {
        {0.01, 0.1, 0.01},
        {0.2, 0.9, 0.1},
        {0.91, 1, 0.01}
    };
    public static final double[][] COUNT_SEARCH_SPACES = {
        {1, 20, 1}
    };

    private final Map<String, EvaluationProperty> properties = new LinkedHashMap<>();

    private static final Map<String, Evaluation> evaluations = new HashMap<>();

    // Instance
    private final CommandSender sender;
    private final ConfigurationSection goals;
    private final Plugin plugin;
    private final MagicController magic;
    private final List<Evaluation> runResults = new ArrayList<>();
    private final List<Evaluation> results = new ArrayList<>();
    private final int repeat;

    public EvaluateTask(CommandSender sender, Plugin plugin, MagicController magic, ConfigurationSection goals, int repeat) {
        this.sender = sender;
        this.goals = goals;
        this.plugin = plugin;
        this.magic = magic;
        this.repeat = repeat;

        EvaluationProperty.register(properties, "COUNT_FACTOR", SearchFactors.class, SearchFactors.COUNT_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "WORD_FACTOR", SearchFactors.class, SearchFactors.WORD_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "SIMILARITY_FACTOR", SearchFactors.class, SearchFactors.SIMILARITY_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "COUNT_WEIGHT", SearchFactors.class, SearchFactors.COUNT_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "WORD_WEIGHT", SearchFactors.class, SearchFactors.WORD_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "MIN_SIMILARITY", SearchFactors.class, SearchFactors.MIN_SIMILARITY, SCALE_SEARCH_SPACES);
        EvaluationProperty.register(properties, "COUNT_MAX", SearchFactors.class, SearchFactors.COUNT_MAX, COUNT_SEARCH_SPACES);

        EvaluationProperty.register(properties, "CONTENT_FACTOR", SearchFactors.class, SearchFactors.CONTENT_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TAG_FACTOR", SearchFactors.class, SearchFactors.TAG_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TITLE_FACTOR", SearchFactors.class, SearchFactors.TITLE_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "CONTENT_WEIGHT", SearchFactors.class, SearchFactors.CONTENT_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TAG_WEIGHT", SearchFactors.class, SearchFactors.TAG_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TITLE_WEIGHT", SearchFactors.class, SearchFactors.TITLE_WEIGHT, WEIGHT_SEARCH_SPACES);

        EvaluationProperty.register(properties, "RARITY_FACTOR", SearchFactors.class, SearchFactors.RARITY_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TOPIC_RARITY_FACTOR", SearchFactors.class, SearchFactors.TOPIC_RARITY_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "LENGTH_FACTOR", SearchFactors.class, SearchFactors.LENGTH_FACTOR, FACTOR_SEARCH_SPACES);
        EvaluationProperty.register(properties, "RARITY_WEIGHT", SearchFactors.class, SearchFactors.RARITY_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "TOPIC_RARITY_WEIGHT", SearchFactors.class, SearchFactors.TOPIC_RARITY_WEIGHT, WEIGHT_SEARCH_SPACES);
        EvaluationProperty.register(properties, "LENGTH_WEIGHT", SearchFactors.class, SearchFactors.LENGTH_WEIGHT, WEIGHT_SEARCH_SPACES);
    }

    @Override
    public void run() {
        try {
            if (repeat <= 0) {
                runSingleEvaluation(sender);
                return;
            }
            for (int i = 0; i < repeat; i++) {
                runEvaluation(sender);
                if (i < repeat - 1) {
                    int remaining = repeat - i;
                    sender.sendMessage(ChatColor.YELLOW + "Remaining runs: "+ ChatColor.GOLD + remaining);
                }
                runResults.clear();
            }
            if (repeat <= 1) {
                sender.sendMessage(ChatColor.GOLD + " Finished.");
            } else {
                Collections.sort(results);
                Evaluation top = results.get(0);
                sender.sendMessage("Finished, applying best selections over all runs from score of "
                        + ChatColor.GREEN + String.format(NUMERIC_FORMAT, top.getRatio())
                        + ChatColor.GRAY + " | "
                        + ChatColor.AQUA + String.format(NUMERIC_FORMAT, top.getScore()));
                double values[] = top.getValues();
                int i = 0;
                for (EvaluationProperty property : properties.values()) {
                    double value = values[i++];
                    String valueDesc = PRECISE_FORMAT.format(value);
                    String defaultDesc = PRECISE_FORMAT.format(property.getDefaultValue());
                    boolean changed = !valueDesc.equals(defaultDesc);
                    ChatColor titleColor = changed ? ChatColor.AQUA : ChatColor.DARK_AQUA;
                    sender.sendMessage(titleColor + property.getDescription() + ChatColor.GRAY + " from "
                        + ChatColor.DARK_GREEN + defaultDesc
                        + ChatColor.GRAY + " to " + ChatColor.GREEN + valueDesc);
                    if (changed) {
                        property.setDefaultValue(value);
                    }
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
        Evaluation evaluation = evaluate("", goals, 0);
        sender.sendMessage("Score: " + ChatColor.GREEN + String.format(NUMERIC_FORMAT, evaluation.getRatio())
            + ChatColor.GRAY + " | "
            + ChatColor.AQUA + String.format(NUMERIC_FORMAT, evaluation.getScore()));
        Set<String> missing = evaluation.getMissingTopics();
        if (!missing.isEmpty()) {
            sender.sendMessage("Missing: " + StringUtils.join(missing, " | "));
        }
    }

    private void runEvaluation(CommandSender sender) throws NoSuchFieldException, IllegalAccessException {
        for (EvaluationProperty property : properties.values()) {
            runEvaluation(sender, goals, property);
        }
        Collections.sort(runResults);
        Evaluation top = runResults.get(0);
        sender.sendMessage("Applying recommended changes from score of "
                + ChatColor.GREEN + String.format(NUMERIC_FORMAT, top.getRatio())
                + ChatColor.GRAY + " | "
                + ChatColor.AQUA + String.format(NUMERIC_FORMAT, top.getScore()));
        double values[] = top.getValues();
        int i = 0;
        for (EvaluationProperty property : properties.values()) {
            double value = values[i++];
            String valueDesc = PRECISE_FORMAT.format(value);
            String defaultDesc = PRECISE_FORMAT.format(property.getDefaultValue());
            boolean changed = !valueDesc.equals(defaultDesc);
            ChatColor titleColor = changed ? ChatColor.AQUA : ChatColor.DARK_AQUA;
            sender.sendMessage(titleColor + property.getDescription() + ChatColor.GRAY + " from "
                + ChatColor.DARK_GREEN + defaultDesc
                + ChatColor.GRAY + " to " + valueDesc);
            if (changed) {
                property.setDefaultValue(value);
            }
        }
    }

    private void runEvaluation(CommandSender sender, ConfigurationSection goals, EvaluationProperty property)
            throws NoSuchFieldException, IllegalAccessException {
        String propertyName = property.getProperty();
        List<Double> values = property.getSearchValues();
        sender.sendMessage(ChatColor.DARK_AQUA + "Searching " + ChatColor.AQUA + propertyName
            + ChatColor.DARK_AQUA + " from " + ChatColor.GREEN + values.get(0)
            + ChatColor.DARK_AQUA + " to " + ChatColor.GREEN + values.get(values.size() - 1)
            + ChatColor.DARK_AQUA + " currently set to " + ChatColor.YELLOW + property.getDefaultValue());
        List<Evaluation> evaluations = new ArrayList<>();
        for (double value : values) {
            property.set(value);
            evaluations.add(evaluate(propertyName, goals, value));
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
        }
        runResults.addAll(evaluations);
        results.addAll(evaluations);
    }

    private void showEvaluation(CommandSender sender, List<Evaluation> evaluations) {
        Collections.sort(evaluations);
        String headerRow = "";
        String ratioRow = "";
        String scoreRow = "";
        for (Evaluation evaluation : evaluations) {
            ChatColor color = evaluation.hasMissingTopics() ? ChatColor.RED : ChatColor.AQUA;
            headerRow += color + ChatUtils.getFixedWidth(PRECISE_FORMAT.format(evaluation.getValue()), NUMERIC_WIDTH);
            if (evaluation.isCached()) {
                color = evaluation.hasMissingTopics() ? ChatColor.DARK_RED : ChatColor.DARK_GREEN;
            } else {
                color = evaluation.hasMissingTopics() ? ChatColor.RED : ChatColor.GREEN;
            }
            ratioRow += color + ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getRatio()), NUMERIC_WIDTH);
            scoreRow += color + ChatUtils.getFixedWidth(String.format(NUMERIC_FORMAT, evaluation.getScore()), NUMERIC_WIDTH);
        }
        sender.sendMessage(headerRow);
        int totalWidth = NUMERIC_WIDTH * evaluations.size();
        sender.sendMessage(StringUtils.repeat("_", totalWidth));
        sender.sendMessage(ratioRow);
        sender.sendMessage(scoreRow);
    }

    private Evaluation evaluate(String propertyKey, ConfigurationSection goals, double value) throws NoSuchFieldException, IllegalAccessException {
        Set<String> goalKeys = goals.getKeys(true);
        Help help = magic.getMessages().getHelp();
        help.resetStats();

        Evaluation evaluation = new Evaluation(propertyKey, value, properties.values());
        Evaluation existing = evaluations.get(evaluation.getKey());
        if (existing != null) {
            return new Evaluation(existing, value);
        }

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
        evaluations.put(evaluation.getKey(), evaluation);
        return evaluation;
    }
}
