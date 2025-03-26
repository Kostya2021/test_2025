package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class LobbyPlayerServiceImpl extends BasePlayerService {
    @Override
    public void dismissEmployee(Player player, Employee employee) {
        // Do nothing
    }

    @Override
    public void generateFirstEmployeesForPlayers() {
        // Do nothing
    }
}