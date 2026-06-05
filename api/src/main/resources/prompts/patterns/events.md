USE WHEN: the plugin reacts to game events (joins, deaths, blocks, damage, etc.).

### Event listeners

Implement `Listener`, annotate handlers with `@EventHandler`, and register in `onEnable()` — an unregistered listener silently never fires.

```java
public class CombatListener implements Listener {
    private final MyPlugin plugin;
    public CombatListener(MyPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();           // may be null (environmental death)
        if (killer != null) {
            // award/steal here; resolve players by UUID if you store them
        }
    }

    @EventHandler(ignoreCancelled = true)             // skip already-cancelled events
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // load player data, validate inventory, etc.
    }
}
```

Register once in onEnable():
```java
getServer().getPluginManager().registerEvents(new CombatListener(this), this);
```

Rules:
- Only set `priority`/`ignoreCancelled` when you actually need ordering. Default priority is NORMAL.
- Never call `event.setCancelled(true)` on an event that doesn't implement `Cancellable`.
- Store player UUIDs, not `Player` objects: `UUID id = p.getUniqueId();` then `Bukkit.getPlayer(id)` on use.
- On a reload command, don't double-register listeners; register them once in onEnable only.
- Import the exact event class, e.g. `org.bukkit.event.entity.PlayerDeathEvent`, `org.bukkit.event.player.PlayerJoinEvent`, plus `org.bukkit.event.EventHandler` and `org.bukkit.event.Listener`.
