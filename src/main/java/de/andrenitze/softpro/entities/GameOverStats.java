package de.andrenitze.softpro.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.sql.Time;
import java.util.UUID;

@Entity
public class GameOverStats {
    @Id
    private UUID id;
    private Integer deliveredProjects;
    private Integer projectsVolume;
    private String playerId;
    private String ipAddress;
    private Time finishedAt;

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

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Time getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Time finishedAt) {
        this.finishedAt = finishedAt;
    }
}
