package de.andrenitze.softpro.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AccountingService {

    private final List<AccountingEntry> entries = new ArrayList<>();

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(AccountingEntry entry) {
        entries.add(entry);
    }

    public List<AccountingEntry> getAllEntriesByPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .collect(Collectors.toList());
    }
}
