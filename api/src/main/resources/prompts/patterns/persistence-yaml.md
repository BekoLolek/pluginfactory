USE WHEN: the plugin must persist data across restarts (player balances, points, claimed items) and the plan has no real database.

### Flat-file persistence with a separate YamlConfiguration

Keep player/runtime DATA in its own file (e.g. data.yml), separate from config.yml (settings). Load on enable, save on disable (and periodically).

```java
private File dataFile;
private FileConfiguration data;

private void loadData() {
    dataFile = new File(getDataFolder(), "data.yml");
    if (!dataFile.exists()) {
        getDataFolder().mkdirs();
        try { dataFile.createNewFile(); } catch (IOException ex) { getLogger().severe("Could not create data.yml"); }
    }
    data = YamlConfiguration.loadConfiguration(dataFile);
}

private void saveData() {
    try { data.save(dataFile); }
    catch (IOException ex) { getLogger().severe("Could not save data.yml: " + ex.getMessage()); }
}

// Read/write keyed by player UUID string:
public int getFragments(UUID id) { return data.getInt("players." + id + ".fragments", 0); }
public void setFragments(UUID id, int amount) { data.set("players." + id + ".fragments", amount); }
```

Lifecycle:
```java
@Override public void onEnable()  { loadData(); /* ... */ }
@Override public void onDisable() { saveData(); }
```

Notes:
- Import `org.bukkit.configuration.file.YamlConfiguration`, `java.io.File`, `java.io.IOException`, `java.util.UUID`.
- For frequent writes, autosave on a timer (see scheduler pattern) and do the disk `save()` asynchronously; build the in-memory map on the main thread.
- Always key player data by `UUID`, never by name (names change).
