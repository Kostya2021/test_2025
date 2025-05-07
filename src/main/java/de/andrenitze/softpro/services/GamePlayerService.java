package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.players.Player;

public interface GamePlayerService extends PlayerService {
    Player getRandomPlayer();
    Player getPreviousState(Player player);
    void updatePreviousState(Player player);
    void generateFirstEmployeesForPlayers();
}
