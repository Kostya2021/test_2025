package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentMap;

/**
 * Service für die Verwaltung von Finanzen und Buchhaltung.
 */
public interface AccountingService {
    void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players);
}