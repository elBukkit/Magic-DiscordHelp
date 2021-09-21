package com.elmakers.mine.bukkit.plugins.help;

import java.util.List;

import com.elmakers.mine.bukkit.utility.help.HelpTopicMatch;

public class EvaluationScore {
    protected int goals;
    protected int matches;
    protected double score;

    public void addResults(List<HelpTopicMatch> matches, String goal) {
        double score = getScore(matches, goal);
        addScore(score);
    }

    public void addScore(double score) {
        this.goals++;
        this.score += score;
        if (score > 0) {
            this.matches++;
        }
    }

    public double getScore(List<HelpTopicMatch> matches, String goal) {
        if (matches.isEmpty()) return 0;

        HelpTopicMatch first = matches.get(0);
        double score;
        if (first.getTopic().getKey().equals(goal)) {
            score = first.getRelevance();
            if (matches.size() > 1) {
                HelpTopicMatch second = matches.get(1);
                score -= second.getRelevance();
            }
        } else {
            score = -first.getRelevance();
            for (HelpTopicMatch match : matches) {
                if (match.getTopic().getKey().equals(goal)) {
                    score += match.getRelevance();
                    break;
                }
            }
        }
        return score;
    }

    public int getMatches() {
        return matches;
    }
}
