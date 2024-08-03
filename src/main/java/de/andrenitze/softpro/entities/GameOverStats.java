package de.andrenitze.softpro.entities;

import de.andrenitze.softpro.types.OptionVoteDistribution;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class GameOverStats {
    private Integer id;
    private Integer deliveredProjects = 0;
    private Integer projectsVolume = 0;
    private String report = "";
    private String playerName;
    private String ipAddress;
    private Date finishedAt;
    private String gameId;
    private Integer survivedDays = 0;
    private Integer playedSeconds = 0;
    private Map<Integer, List<OptionVoteDistribution>> communityVotes;
    private Integer level = 0;
    private String period;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDeliveredProjects() {
        return deliveredProjects;
    }

    public void setDeliveredProjects(Integer deliveredProjects) {
        this.deliveredProjects = deliveredProjects;
    }

    public Integer getProjectsVolume() {
        return projectsVolume;
    }

    public void setProjectsVolume(Integer projectsVolume) {
        this.projectsVolume = projectsVolume;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerId) {
        this.playerName = playerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Date getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Integer getSurvivedDays() {
        return survivedDays;
    }

    public void setSurvivedDays(Integer survivedDays) {
        this.survivedDays = survivedDays;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public void setPlayedSeconds(Integer playedSeconds) {
        this.playedSeconds = playedSeconds;
    }

    public Integer getPlayedSeconds() {
        return playedSeconds;
    }

    public void setCommunityVotes(Map<Integer, List<OptionVoteDistribution>> distributions) {
        this.communityVotes = distributions;
    }

    public Map<Integer, List<OptionVoteDistribution>> getCommunityVotes() {
        return communityVotes;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getPeriod() {
        return period;
    }
}
