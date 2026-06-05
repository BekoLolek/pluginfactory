USE WHEN: the plugin registers any command, especially one with sub-commands (/x give|list|reload).

### Command executor + tab completion + sub-command dispatch

One command is registered in plugin.yml; sub-commands are `args[0]`. Implement both `CommandExecutor` and `TabCompleter`.

```java
public class WeaponsCommand implements CommandExecutor, TabCompleter {
    private final CustomWeapons plugin;
    public WeaponsCommand(CustomWeapons plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /weapons <give|list|reload>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give"   -> { return handleGive(sender, args); }
            case "list"   -> { return handleList(sender); }
            case "reload" -> { return handleReload(sender); }
            default -> { sender.sendMessage("§cUnknown sub-command: " + args[0]); return true; }
        }
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customweapons.give")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 3) { sender.sendMessage("§eUsage: /weapons give <player> <weapon>"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not online: " + args[1]); return true; }
        // ... look up weapon by args[2]; if unknown, message and return true without giving anything
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give", "list", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("give"))
            return new ArrayList<>(plugin.getWeaponIds());   // your known ids
        return Collections.emptyList();
    }
}
```

Register both in `onEnable()` (null-check `getCommand` — it returns null if the command is missing from plugin.yml):
```java
PluginCommand cmd = getCommand("weapons");
if (cmd != null) { cmd.setExecutor(new WeaponsCommand(this)); cmd.setTabCompleter(new WeaponsCommand(this)); }
```

Notes: import `org.bukkit.command.CommandExecutor`, `TabCompleter`, `Command`, `CommandSender`, `org.bukkit.command.PluginCommand`. Always `return true` after handling (returning false dumps the plugin.yml usage string). Command NAME in plugin.yml must be a single word.
