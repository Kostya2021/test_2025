package de.andrenitze.softpro.domains;

import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
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
    private Integer score = 0;
}
