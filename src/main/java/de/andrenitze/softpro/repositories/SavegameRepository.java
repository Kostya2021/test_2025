package de.andrenitze.softpro.repositories;

import de.andrenitze.softpro.domains.Savegame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavegameRepository extends JpaRepository<Savegame, Long> {
    List<Savegame> findAllByUserId(String userId);
    Optional<Savegame> findTopByUserIdOrderByLevelDesc(String userId);
}