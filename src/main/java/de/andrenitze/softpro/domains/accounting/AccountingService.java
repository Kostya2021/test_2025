package de.andrenitze.softpro.domains.accounting;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
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

        // Always add 1 to day to make sure the entry is processed (entry could be lost if added between ticks)
        entry.setDay(entry.getDay() + 1);

        entries.add(entry);
    }

    public List<AccountingEntry> getAllEntriesByPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .collect(Collectors.toList());
    }

    public void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players, int currentTick) {
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
                player.subtractFunds((float) rent + insurance);

                game.getMessagingService().sendFundsUpdateToPlayer(player);
            });
        }
    }
}
