package de.andrenitze.softpro.domains.decisions;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class DecisionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int decisionId;
    private int optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_decision_id")
    private PlayerDecision playerDecision;
}
