package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import de.andrenitze.softpro.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AccountingService {

    private final List<AccountingEntry> entries = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Adds a new entry to the in-memory list
    public synchronized void addEntry(Player player, int day, int amount, AccountCategory category, String description) {
        AccountingEntry entry = new AccountingEntry(player, day, amount, category, description);
        entries.add(entry);
    }

    // Returns all entries
    public List<AccountingEntry> getAllEntries() {
        return entries;
    }

    // Sums all amounts (int)
    public int getTotalBalance() {
        return entries.stream()
                .mapToInt(AccountingEntry::getAmount)
                .sum();
    }

    // Filters entries by category
    public List<AccountingEntry> getEntriesByCategory(AccountCategory category) {
        List<AccountingEntry> result = new ArrayList<>();
        for (AccountingEntry e : entries) {
            if (e.getCategory() == category) {
                result.add(e);
            }
        }
        return result;
    }

    // Saves current list of entries to a JSON file
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
