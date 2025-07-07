package de.andrenitze.softpro.domains.projects;

import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Slf4j
public class Project implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.PACKAGE)
    private Integer id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    @EqualsAndHashCode.Include
    private String name;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private int totalValue;
    @Getter
    @Setter
    private int earnedValue;
    private final List<EarnedValueHistoryEntry> earnedValueHistory = new ArrayList<>();
    private boolean tenderProcess;
    @Getter
    @Setter
    private int tenderDeadlineInDays;

    // Deadline: In how many days the project has to be finished,
    // measured from the day the project was acquired. (0 = no deadline)
    @Getter
    @Setter
    private int deadline;
    private final ArrayList<Player> involvedParties = new ArrayList<>();

    // acquiredAt != 0 means the project has been acquired by a player
    @Setter
    @Getter
    private int acquiredAt;
    @Getter
    @Setter
    private int startedAt;
    @Setter
    @Getter
    private int completedAt = 0;
    @Getter
    @Setter
    private int quality;
    @Getter
    @Setter
    private int publishedAt;
    @Getter
    @Setter
    private float penalty;
    @Getter
    @Setter
    private float profit;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private RiskLevel risk;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private ProjectType type;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String domain;
    @Getter @Setter
    private boolean hasBeenRiskAssessed = false;
    @Getter
    private final List<Problem> problems = new ArrayList<>();
    @Getter
    @Setter
    private List<ProgressEstimate> progressEstimates = new ArrayList<>();
    @Getter
    @Setter
    private int cancelledAt = 0;
    @Getter
    @Setter
    private String cancelledBy;

    Project() {}

    public void setTenderProcess(boolean hasTenderProcess) {
        this.tenderProcess = hasTenderProcess;

        if (hasTenderProcess) {
            this.tenderDeadlineInDays = 20;
        } else {
            this.tenderDeadlineInDays = -1;
        }
    }

    public void decreaseTimeLeftForTender() {
        --this.tenderDeadlineInDays;
    }

    /**
     * Several companies can be associated with the same project.
     * Several companies can take part in the tender process.
     * After the tender, several companies can work on the project together.
     */
    public void addParty(Player player) {
        if (!involvedParties.contains(player)) {
            involvedParties.add(player);
        }
    }

    public List<Player> getInvolvedPlayers() {
        return involvedParties;
    }
    public int getId() {
        return id;
    }
    public RiskLevel getRiskLevel() {
        return risk;
    }


    public void addEarnedValue(int addedValue, int tick) {
        int newEarnedValue = Math.clamp(getEarnedValue() + addedValue, 0, getTotalValue());
        setEarnedValue(newEarnedValue);

        if (isCompleted()) {
            setCompletedAt(tick);
        }

        // Add earned value to the correct tick in the history
        // This is required because earnedValue can be added for multiple employees in one tick
        earnedValueHistory.stream()
                .filter(entry -> entry.getTick() == tick)
                .findFirst()
                .ifPresentOrElse(
                        entry -> entry.addValue(addedValue),
                        () -> earnedValueHistory.add(new EarnedValueHistoryEntry(tick, getEarnedValue()))
                );
    }

    public boolean hasNoTenderProcess() {
        return !tenderProcess;
    }

    public boolean isCompleted() {
        return (getTotalValue() - getEarnedValue() <= 0);
    }

    public boolean playerWasInvolved(Player player) {
        return getInvolvedPlayers().contains(player);
    }

    public boolean hasOnboardingEmployees(List<Employee> employees) {
        if (this.getType() == ProjectType.COMPLIANCE) {
            return false;
        }

        // After "safe period": Does any of the employees need on-boarding?
        int safePeriodInDays = (int) (GameParameters.SAFE_PERIOD_PERCENT * getScheduledDuration())
                + GameParameters.ASSIGNMENT_TIME_IN_DAYS;
        for (Employee employee : employees) {
            // Employee has no experience in this project and needs to be trained
            if (employee.getExperienceByProject(this) <= safePeriodInDays) {
                return true;
            }
        }

        return false;
    }

    public boolean isRampingUp(int currentTick) {
        // No extra on-boarding effort is assigned at the beginning of the project for the beginning of a project
        // (time to allocate staff to project, also general ramp-up, s. Rule #2)
        // Safe period (10%). No training required.
        return currentTick <= (getScheduledDuration() * GameParameters.SAFE_PERIOD_PERCENT
                + getAcquiredAt()
                + GameParameters.ASSIGNMENT_TIME_IN_DAYS);
    }

    public int getScheduledDuration() {
        return getDeadline() - getAcquiredAt();
    }

    public boolean hasBeenStarted() {
        return getStartedAt() > 0;
    }

    public void addProblem(Problem problem) {
        problems.add(problem);
    }

    public List<Problem> getUnsolvedProblems() {
        return problems.stream().filter(problem -> !problem.isSolved()).toList();
    }

    public void addProgressEstimate(int tick, int estimate) {
        progressEstimates.add(new ProgressEstimate(tick, estimate));
    }

    public void estimateProgress(int currentTick) {
        // Estimate progress (0-100) based on the earned value
        int estimate = (int) (100.0 * getEarnedValue() / getTotalValue());

        // Project has not been started yet
        if (!hasBeenStarted()) {
            return;
        }

        // Add variance of up to 70% based on risk level (e.g., the more risk the more variance)
        float riskMultiplier = switch (getRiskLevel()) {
            case LOW -> 0.3f;
            case MEDIUM -> 0.5f;
            case HIGH -> 0.7f;
            case EXTREME -> 1.0f;
        };


        int adjustedEstimate = (int) (riskMultiplier * estimate);
        estimate += adjustedEstimate > 0 ? RANDOM.nextInt(adjustedEstimate) : 0;
        addProgressEstimate(currentTick, Math.min(90, estimate)); // 90% is the maximum progress estimate
    }

    public void removeParty(Player player) {
        this.involvedParties.remove(player);
    }

    public boolean hasProgressChanged(Project other) {
        return this.earnedValue != other.earnedValue ||
                !Objects.equals(this.progressEstimates, other.progressEstimates);
    }

    public boolean hasStatusChanged(Project other) {
        return this.startedAt != other.startedAt ||
                this.completedAt != other.completedAt ||
                this.cancelledAt != other.cancelledAt;
    }

    public boolean hasEconomyChanged(Project other) {
        return this.totalValue != other.totalValue ||
                Float.compare(this.penalty, other.penalty) != 0 ||
                Float.compare(this.profit, other.profit) != 0;
    }

    public boolean hasMetaChanged(Project other) {
        return this.tenderProcess != other.tenderProcess ||
                this.tenderDeadlineInDays != other.tenderDeadlineInDays ||
                this.deadline != other.deadline ||
                this.acquiredAt != other.acquiredAt ||
                this.quality != other.quality ||
                this.publishedAt != other.publishedAt ||
                !Objects.equals(this.risk, other.risk) ||
                !Objects.equals(this.cancelledBy, other.cancelledBy) ||
                !Objects.equals(this.problems, other.problems);
    }

    public boolean hasChanged(Project other) {
        if (other == null) return true;

        return hasProgressChanged(other)
                || hasStatusChanged(other)
                || hasEconomyChanged(other)
                || hasMetaChanged(other);
    }

    public void clearInvolvedParties() {
        this.involvedParties.clear();
    }

    public boolean isFailed() {
        return !isCompleted() && getCancelledAt() != 0;
    }
}
