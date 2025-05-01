package de.andrenitze.softpro.domains.decisions;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class PlayerDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private int level;

    @OneToMany(mappedBy = "playerDecision", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DecisionEntry> decisions = new ArrayList<>();
}
