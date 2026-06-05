USE WHEN: the plugin shows a sidebar scoreboard, below-name display, or uses teams (colors, collision, friendly fire).

### Scoreboard sidebar + teams

```java
ScoreboardManager manager = Bukkit.getScoreboardManager();
Scoreboard board = manager.getNewScoreboard();

Objective obj = board.registerNewObjective("stats", Criteria.DUMMY, "§6§lStats");
obj.setDisplaySlot(DisplaySlot.SIDEBAR);
obj.getScore("§aKills:").setScore(5);                  // each line is an entry with a score
obj.getScore("§cDeaths:").setScore(2);

player.setScoreboard(board);                            // assign per player
```

Teams (e.g. colored names, disable friendly fire):
```java
Team team = board.getTeam("red");
if (team == null) team = board.registerNewTeam("red");
team.setColor(ChatColor.RED);
team.setAllowFriendlyFire(false);
team.addEntry(player.getName());                        // entries are player NAMES for teams
```

Notes:
- Import `org.bukkit.scoreboard.*` (`Scoreboard`, `ScoreboardManager`, `Objective`, `Team`, `DisplaySlot`, `Criteria`), `org.bukkit.ChatColor`.
- Sidebar lines must be unique strings; pad with color codes if you need duplicates.
- Rebuild/refresh on a timer rather than every event for performance.
- Reset with `player.setScoreboard(manager.getMainScoreboard())` when done.
