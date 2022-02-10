package de.andrenitze.softpro.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;
import java.util.UUID;

@Entity
public class GameOverStats {
    @Id
    @GeneratedValue()
    private UUID id;
    private Integer deliveredProjects = 0;
    private Integer projectsVolume = 0;
    private String playerName;
    private String ipAddress;
    private Date finishedAt;
    private String gameId;
    private Integer survivedDays = 0;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
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
}
