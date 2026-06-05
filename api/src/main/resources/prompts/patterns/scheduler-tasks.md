USE WHEN: the plugin runs repeating/delayed work (autosave timers, countdowns, periodic effects).

### Scheduling with BukkitRunnable

Use the Bukkit scheduler, never raw Java threads. 20 ticks = 1 second.

```java
// Repeating sync task (safe to touch Bukkit API):
BukkitTask task = new BukkitRunnable() {
    @Override public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // periodic logic on the main thread
        }
    }
}.runTaskTimer(plugin, 0L, 20L);     // start now, repeat every second

// Delayed one-shot:
new BukkitRunnable() {
    @Override public void run() { /* later */ }
}.runTaskLater(plugin, 100L);        // after 5 seconds

// Async ONLY for file/network I/O — never call Bukkit API from async:
new BukkitRunnable() {
    @Override public void run() {
        saveDataToDisk();                                  // pure I/O, no Bukkit calls
        new BukkitRunnable() {                             // hop back to main thread for Bukkit work
            @Override public void run() { notifyPlayers(); }
        }.runTask(plugin);
    }
}.runTaskAsynchronously(plugin);
```

Cancel tasks on disable to avoid leaks:
```java
@Override public void onDisable() { Bukkit.getScheduler().cancelTasks(plugin); }
```

Notes: import `org.bukkit.scheduler.BukkitRunnable`, `org.bukkit.scheduler.BukkitTask`. Keep a reference to long-lived tasks so you can `.cancel()` them. Autosave belongs on a timer here, not on every change.
