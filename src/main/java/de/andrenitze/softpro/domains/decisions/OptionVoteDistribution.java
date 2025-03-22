package de.andrenitze.softpro.domains.decisions;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OptionVoteDistribution {
    private int decisionId;
    private int selectedOption;
    private int voteCount;
    private double percentage;

    public OptionVoteDistribution(int decisionId, int selectedOption, int voteCount, double percentage) {
        this.decisionId = decisionId;
        this.selectedOption = selectedOption;
        this.voteCount = voteCount;
        this.percentage = percentage;
    }

    @Override
    public String toString() {
        return "OptionVoteDistribution{" +
                "selectedOption=" + selectedOption +
                ", voteCount=" + voteCount +
                ", percentage=" + percentage +
                '}';
    }
}
