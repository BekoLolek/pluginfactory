USE WHEN: an ability/command/item may only be used every N seconds per player.

### Per-player cooldowns

Track the next-allowed timestamp per player UUID in memory.

```java
private final Map<UUID, Long> cooldownUntil = new HashMap<>();
private static final long COOLDOWN_MS = 5_000L;       // make configurable

public boolean isOnCooldown(Player player) {
    long now = System.currentTimeMillis();
    Long until = cooldownUntil.get(player.getUniqueId());
    return until != null && until > now;
}

public long remainingSeconds(Player player) {
    Long until = cooldownUntil.get(player.getUniqueId());
    if (until == null) return 0;
    return Math.max(0, (until - System.currentTimeMillis()) / 1000);
}

public void applyCooldown(Player player) {
    cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);
}

// usage:
if (isOnCooldown(player)) {
    player.sendMessage("§cWait " + remainingSeconds(player) + "s.");
    return;
}
applyCooldown(player);
// ... run the ability
```

Notes: in-memory cooldowns reset on restart (usually fine). Key by `UUID`. Clean up on `PlayerQuitEvent` if the map could grow large. Read `COOLDOWN_MS` from config for tunability.
