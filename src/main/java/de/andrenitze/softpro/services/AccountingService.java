package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.players.Player;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Service für die Verwaltung von Finanzen und Buchhaltung.
 */
public interface AccountingService {
    void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players, int gameTick, int gameLevel);
    List<AccountingEntry> getAccountingEntriesByTick(int gameTick, Map<WebSocket, Player> players);
}