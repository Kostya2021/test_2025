package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.services.AccountingService;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static de.andrenitze.softpro.Main.logger;

public class AccountingServiceImpl implements AccountingService {
    private final List<AccountingEntry> entries = new ArrayList<>();

    public AccountingServiceImpl() {
    }

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(AccountingEntry entry) {
        if (entry == null) {
            logger.error("Tried to add a null entry.");
            return;
        }

        if (entry.getAmount() <= 0) {
            return;
        }

        // Always add 1 to day to make sure the entry is processed (entry could be lost if added between ticks)
        entry.setDay(entry.getDay() + 1);

        entries.add(entry);
    }

    public List<AccountingEntry> getAllEntriesByPlayer(Player player) {
        return entries.stream()
                .filter(e -> e.getPlayer().equals(player))
                .toList();
    }

    public void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players, int gameTick, int gameLevel) {
        if (d.getDayOfMonth() == 1) {
            players.forEach((ignored, player) -> {
                // Calculate and subtract salaries
                int salaries = player.calculateAndSubtractSalaries();
                AccountingEntry salaryEntry = new AccountingEntry(player, gameTick, gameLevel, salaries, AccountCategory.SALARIES,
                        TransactionType.DEBIT, "Monthly salaries");
                addEntry(salaryEntry);

                // Office rent (fixed costs, rises with level)
                int rent = 500 * (gameLevel-1);
                AccountingEntry rentEntry = new AccountingEntry(player, gameTick, gameLevel, rent, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Office rent");
                addEntry(rentEntry);

                // Insurance (fixed costs, rises with level)
                int insurance = 150 * gameLevel;
                AccountingEntry insuranceEntry = new AccountingEntry(player, gameTick, gameLevel, insurance, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Insurance");
                addEntry(insuranceEntry);

                // Subtract rent and insurance from funds (not handled by accounting service)
                player.subtractFunds((float) rent + insurance);
            });
        }
    }

    public List<AccountingEntry> getAccountingEntriesByTick(int gameTick, Map<WebSocket, Player> players) {
        return players.values().stream()
                .flatMap(player -> getAllEntriesByPlayer(player).stream())
                .filter(entry -> entry.getDay() == gameTick)
                .toList();
    }

    public List<AccountingEntry> getNewEntriesByPlayer(Player player, int tick) {
        return entries.stream()
                .filter(e -> e.getPlayer().equals(player) && e.getDay() == tick)
                .toList();
    }
}