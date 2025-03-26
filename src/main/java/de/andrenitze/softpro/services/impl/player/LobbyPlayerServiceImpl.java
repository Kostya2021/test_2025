package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import org.springframework.stereotype.Service;

@Service
// Lobby player is currently exactly the same as the abstract game player service.
public class LobbyPlayerServiceImpl extends BasePlayerService {
    @Override
    public void dismissEmployee(Player player, Employee employee) {}

    @Override
    public void generateFirstEmployeesForPlayers() {}
}