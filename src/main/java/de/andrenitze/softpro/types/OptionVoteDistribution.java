package de.andrenitze.softpro.types;

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

    public int getSelectedOption() {
        return selectedOption;
    }

    public void setSelectedOption(int selectedOption) {
        this.selectedOption = selectedOption;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public int getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(int decisionId) {
        this.decisionId = decisionId;
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
