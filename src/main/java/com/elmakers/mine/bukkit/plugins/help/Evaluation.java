package com.elmakers.mine.bukkit.plugins.help;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;

public class Evaluation extends EvaluationScore implements Comparable<Evaluation> {
    private final double value;
    private EvaluationScore currentGoal = null;
    private Map<String, EvaluationScore> goalEvaluations = new HashMap<>();

    public Evaluation(double value) {
        this.value = value;
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

    public EvaluationScore getCurrentGoal() {
        return currentGoal;
    }

    @Override
    public int compareTo(@NotNull Evaluation o) {
        if (o.matches == this.matches) {
            return o.score > score ? 1 : (o.score < score ? -1 : 0);
        }
        return o.matches > matches ? 1 : -1;
    }

    public double getRatio() {
        return 100.0 * matches / goals;
    }

    public double getValue() {
        return value;
    }

    public void getMissingTopics(Map<String, Integer> missing) {
        for (Map.Entry<String, EvaluationScore> entry : goalEvaluations.entrySet()) {
            EvaluationScore goalScore = entry.getValue();
            if (goalScore.getMatches() == 0) {
                String topic = entry.getKey();
                Integer count = missing.get(topic);
                if (count == null) count = 1;
                else count++;
                missing.put(topic, count);
            }
        }
    }
}
