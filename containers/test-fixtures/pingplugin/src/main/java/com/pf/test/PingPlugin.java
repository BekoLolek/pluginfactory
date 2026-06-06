package com.pf.test;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal known-good plugin used to prove the functional-test harness. */
public class PingPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("ping").setExecutor(this);
        getCommand("givetoken").setExecutor(this);
        getLogger().info("PingPlugin enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ping")) {
            sender.sendMessage("pong");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("givetoken")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("players only");
                return true;
            }
            ItemStack item = new ItemStack(Material.DIAMOND);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("Magic Token");
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            sender.sendMessage("gave token");
            return true;
        }
        return false;
    }
}
