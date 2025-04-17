package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;

/**
 * Service für die Verwaltung von Mitarbeitern.
 */
public interface EmployeeService {
    void setMessagingService(MessagingService messagingService);
    void dismissEmployee(Player player, Employee employee);
}