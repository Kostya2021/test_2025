package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.services.AccountingService;
import de.andrenitze.softpro.services.GamePlayerService;
import de.andrenitze.softpro.services.MessagingService;
import lombok.Setter;
import org.java_websocket.WebSocket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static de.andrenitze.softpro.Main.logger;

public class AccountingServiceImpl implements AccountingService {
    private final List<AccountingEntry> entries = new ArrayList<>();
    @Setter private MessagingService messagingService;
    @Setter private GamePlayerService playerService;

    public AccountingServiceImpl(MessagingService messagingService, GamePlayerService playerService) {
        this.messagingService = messagingService;
        this.playerService = playerService;
    }

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(AccountingEntry entry) {
        if (entry == null) {
            logger.error("Tried to add a null entry.");
            return;
        }

        if (entry.getAmount() <= 0) {
            logger.error("Tried to add an entry with a non-positive or zero amount.");
            return;
        }

        // Always add 1 to day to make sure the entry is processed (entry could be lost if added between ticks)
        entry.setDay(entry.getDay() + 1);

        entries.add(entry);
    }

    public List<AccountingEntry> getAllEntriesByPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .toList();
    }

    public void processMonthlyPayments(LocalDate d, ConcurrentMap<WebSocket, Player> players, int gameTick, int gameLevel) {
        if (d.getDayOfMonth() == 1) {
            players.forEach((_, player) -> {
                // Calculate and subtract salaries
                int salaries = player.calculateAndSubtractSalaries();
                addEntry(new AccountingEntry(player, gameTick, salaries, AccountCategory.SALARIES,
                        TransactionType.DEBIT, "Monthly salaries"));

                // Office rent (fixed costs, rises with level)
                int rent = 500 * (gameLevel-1);
                addEntry(new AccountingEntry(player, gameTick, rent, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Office rent"));

                // Insurance (fixed costs, rises with level)
                int insurance = 150 * gameLevel;
                addEntry(new AccountingEntry(player, gameTick, insurance, AccountCategory.OVERHEAD,
                        TransactionType.DEBIT, "Insurance"));

                // Subtract rent and insurance from funds (not handled by accounting service)
                player.subtractFunds((float) rent + insurance);

                messagingService.sendFundsUpdateToPlayer(player);
            });
        }
    }

    public List<AccountingEntry> getNewAccountingEntries(int gameTick) {
        List<AccountingEntry> newEntries = new ArrayList<>();
        playerService.getPlayers().forEach((_, player) -> newEntries.addAll(getAllEntriesByPlayer(player.getId()).stream()
                .filter(entry -> entry.getDay() == gameTick)
                .toList()));
        return newEntries;
    }
}
