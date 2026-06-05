USE WHEN: the plugin reads/affects player money via Vault (economy balance, charge, deposit).

### Vault economy integration (soft dependency)

Vault is an OPTIONAL dependency: declare `softdepend: [Vault]` in plugin.yml and degrade gracefully if absent. Vault provides an interface; an economy plugin (EssentialsX, etc.) provides the implementation.

```java
private Economy economy;

private boolean setupEconomy() {
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
        getLogger().warning("Vault not found — economy features disabled.");
        return false;
    }
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) {
        getLogger().warning("No economy provider registered with Vault — economy features disabled.");
        return false;
    }
    economy = rsp.getProvider();
    return true;
}

// usage (guard on economy != null):
if (economy != null && economy.has(player, 100.0)) {
    economy.withdrawPlayer(player, 100.0);
    economy.depositPlayer(target, 100.0);
}
```

pom.xml needs the VaultAPI dependency (provided scope) and the JitPack repo — the build template adds these automatically when the plan lists Vault.

Notes: import `net.milkbowl.vault.economy.Economy`, `org.bukkit.plugin.RegisteredServiceProvider`. Call `setupEconomy()` in `onEnable()`. Never hard-fail if Vault/economy is missing — disable just the economy features.
