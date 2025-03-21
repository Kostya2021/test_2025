package de.andrenitze.softpro.entities;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.andrenitze.softpro.Main.logger;

public class AccountingService {
    private final List<AccountingEntry> entries = new ArrayList<>();
    private final Game game;

    public AccountingService(Game game) {
        this.game = game;
    }

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(AccountingEntry entry) {
        if (entry == null) {
            logger.error("Tried to add a null entry.");
            return;
        }

        if (entry.getAmount() <= 0) {
            logger.error("Tried to add an entry with a non-positive amount.");
            return;
        }

        entries.add(entry);
        logger.debug("Added new entry on day {} with amount {} and category {}.", entry.getDay(), entry.getAmount(), entry.getCategory());
    }

    public List<AccountingEntry> getAllEntriesByPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .collect(Collectors.toList());
    }

    public void processMonthlyPaymentsPerTick(LocalDate d, ConcurrentHashMap<WebSocket, Player> players, int currentTick) {
        if (d.getDayOfMonth() == 1) {
            players.forEach((webSocket, player) -> {
                // Calculate and subtract salaries
                int salaries = player.calculateAndSubtractSalaries();
                addEntry(new AccountingEntry(player, currentTick, salaries, AccountCategory.SALARIES,
                        TransactionType.DEBIT, "Monthly salaries"));

                // Office rent (fixed costs, rises with level)
                int rent = 500 * (game.getLevel()-1);
                addEntry(new AccountingEntry(player, currentTick, rent, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Office rent"));

                // Insurance (fixed costs, rises with level)
                int insurance = 150 * game.getLevel();
                addEntry(new AccountingEntry(player, currentTick, insurance, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Insurance"));

                // Subtract rent and insurance from funds (not handled by accounting service)
                player.subtractFunds(rent + insurance);

                game.getMessagingService().sendFundsUpdateToPlayer(player);
            });
        }
    }
}
