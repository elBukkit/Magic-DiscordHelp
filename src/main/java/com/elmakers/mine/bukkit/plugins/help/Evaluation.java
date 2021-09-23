package com.elmakers.mine.bukkit.plugins.help;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;

public class Evaluation extends EvaluationScore implements Comparable<Evaluation> {
    private final double value;
    private EvaluationScore currentGoal = null;
    private Set<String> missing = null;
    private Map<String, EvaluationScore> goalEvaluations = new HashMap<>();

    public Evaluation(double value) {
        this.value = value;
    }

    public Evaluation(Evaluation evaluation, double value) {
        super(evaluation);
        this.value = value;
        this.currentGoal = evaluation.currentGoal;
        this.goalEvaluations = evaluation.goalEvaluations;
    }

    public void setGoal(String goal) {
        currentGoal = new EvaluationScore();
        goalEvaluations.put(goal, currentGoal);
    }

    public void addResults(List<HelpTopicMatch> matches, String goal) {
        if (currentGoal == null) {
            throw new IllegalStateException("Calling addResults before setGoal");
        }
        double score = getScore(matches, goal);
        addScore(score);
        currentGoal.addScore(score);
    }

    @Override
    public int compareTo(@NotNull Evaluation o) {
        if (o.hasMissingTopics() && !hasMissingTopics()) {
            return -1;
        }
        if (!o.hasMissingTopics() && hasMissingTopics()) {
            return 1;
        }
        if (o.matches == this.matches) {
            return o.score > score ? 1 : (o.score < score ? -1 : 0);
        }
        return o.matches > matches ? 1 : -1;
    }

    public double getRatio() {
        return (double)matches / goals;
    }

    public double getScore() {
        return score;
    }

    public double getValue() {
        return value;
    }

    public void addMissingTopics(Map<String, Integer> missing) {
        Set<String> missingTopics = getMissingTopics();
        for (String topic : missingTopics) {
            Integer count = missing.get(topic);
            if (count == null) count = 1;
            else count++;
            missing.put(topic, count);
        }
    }

    public Set<String> getMissingTopics() {
        if (missing == null) {
            missing = new HashSet<>();
            for (Map.Entry<String, EvaluationScore> entry : goalEvaluations.entrySet()) {
                EvaluationScore goalScore = entry.getValue();
                if (goalScore.getMatches() == 0) {
                    missing.add(entry.getKey());
                }
            }
        }
        return missing;
    }

    public boolean hasMissingTopics() {
        return !getMissingTopics().isEmpty();
    }
}
