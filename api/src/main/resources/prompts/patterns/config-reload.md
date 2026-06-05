USE WHEN: the plugin has any config.yml settings, and especially when it needs a /<plugin> reload command (NEEDS_SETUP / PARTIAL plugins always do).

### Config loading, caching, and reload

Ship a default config, cache values into fields, and re-read them on reload.

```java
private int startingBalance;
private boolean persistData;

@Override
public void onEnable() {
    saveDefaultConfig();      // copies src/main/resources/config.yml on first run; no-op after
    loadConfigValues();
    // ... register commands/listeners
}

private void loadConfigValues() {
    reloadConfig();           // re-read file from disk
    FileConfiguration cfg = getConfig();
    startingBalance = cfg.getInt("starting-balance", 50);      // ALWAYS provide a default
    persistData     = cfg.getBoolean("persistence.enabled", true);
}
```

Reload sub-command (permission op):
```java
if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
    if (!sender.hasPermission("myplugin.reload")) { sender.sendMessage("§cNo permission."); return true; }
    loadConfigValues();       // re-reads every cached field
    reRegisterRecipesIfAny(); // if recipes depend on config, rebuild them here
    sender.sendMessage("§aConfig reloaded.");
    return true;
}
```

Startup validation for NEEDS_SETUP plugins — warn loudly if required sections are empty:
```java
if (getConfig().getConfigurationSection("weapons") == null) {
    getLogger().warning("No 'weapons' configured in config.yml — define them before use. See the generated config.");
}
```

`src/main/resources/config.yml` must exist with commented, realistic defaults so the plugin works on first run:
```yaml
# Starting balance for new players
starting-balance: 50
persistence:
  enabled: true   # keep data across restarts
```

Notes: import `org.bukkit.configuration.file.FileConfiguration`. After writing values at runtime call `saveConfig()`.
