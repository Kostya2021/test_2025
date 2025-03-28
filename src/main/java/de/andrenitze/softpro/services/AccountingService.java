package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Service für die Verwaltung von Finanzen und Buchhaltung.
 */
public interface AccountingService {
    void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players, int gameTick, int gameLevel);
    List<AccountingEntry> getNewAccountingEntries(int gameTick);
}