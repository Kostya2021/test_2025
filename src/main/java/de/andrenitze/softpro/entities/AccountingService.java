package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import de.andrenitze.softpro.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AccountingService {

    private final List<AccountingEntry> entries = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(Player player, int day, int amount, AccountCategory category, String description) {
        AccountingEntry entry = new AccountingEntry(player, day, amount, category, description);
        entries.add(entry);
    }

    public List<AccountingEntry> getAllEntriesByPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .collect(Collectors.toList());
    }

    public List<AccountingEntry> getEntriesByPlayerAndCategory(UUID playerId, AccountCategory category) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .filter(e -> e.getCategory() == category)
                .collect(Collectors.toList());
    }

    public int getTotalBalanceForPlayer(UUID playerId) {
        return entries.stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .mapToInt(AccountingEntry::getAmount)
                .sum();
    }

    public void saveToFile(String filePath) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), entries);
    }

    // Loads entries from a JSON file and replaces the in-memory list
    public void loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            return; // No file present, do nothing
        }
        CollectionType listType = objectMapper.getTypeFactory()
                .constructCollectionType(ArrayList.class, AccountingEntry.class);
        List<AccountingEntry> loadedEntries = objectMapper.readValue(file, listType);
        entries.clear();
        entries.addAll(loadedEntries);
    }
}
