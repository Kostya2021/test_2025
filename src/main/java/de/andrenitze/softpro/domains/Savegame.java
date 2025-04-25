package de.andrenitze.softpro.domains;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "savegames")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Savegame {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private int level;

    @Lob
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String gameStateJson;

    @Column(nullable = false)
    private Instant lastUpdated;
}