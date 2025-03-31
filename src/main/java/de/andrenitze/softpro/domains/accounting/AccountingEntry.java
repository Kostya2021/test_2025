package de.andrenitze.softpro.domains.accounting;

import de.andrenitze.softpro.Player;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AccountingEntry {
    private transient Player player;  // Player; Don't serialize as it's only used for assigning the entry to a player
    private int level;      // Game level
    private int day;        // Game day / tick
    private int amount;     // Positive or negative
    private AccountCategory category;
    private String description;
    private TransactionType transactionType;

    public AccountingEntry(Player player, int day, int amount, AccountCategory category, TransactionType transactionType) {
        this.player = player;
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