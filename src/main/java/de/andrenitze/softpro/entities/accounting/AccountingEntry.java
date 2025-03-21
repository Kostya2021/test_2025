package de.andrenitze.softpro.entities.accounting;

import de.andrenitze.softpro.Player;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class AccountingEntry {
    private UUID playerId;        // Unique identifier for player
    private int level;      // Game level
    private int day;        // Game day / tick
    private int amount;     // Positive or negative
    private AccountCategory category;
    private String description;
    private TransactionType transactionType;

    public AccountingEntry(Player player, int day, int amount, AccountCategory category, TransactionType transactionType) {
        this.playerId = player.getId();
        this.level = player.getLevel();
        this.day = day;
        this.amount = amount;
        this.category = category;
        this.transactionType = transactionType;
    }

    public AccountingEntry(Player player, int day, int amount, AccountCategory category, TransactionType transactionType, String description) {
        this(player, day, amount, category, transactionType);
        this.description = description;
    }
}