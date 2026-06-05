USE WHEN: the plugin opens a chest-style menu / GUI the player clicks (shops, kit selectors, settings).

### Inventory GUI with a custom InventoryHolder (robust ownership check)

Identify your GUIs by a custom `InventoryHolder` rather than the title string — it survives renames and is unambiguous.

```java
public class MenuHolder implements InventoryHolder {
    private final String menuId;
    private Inventory inventory;
    public MenuHolder(String menuId) { this.menuId = menuId; }
    public String getMenuId() { return menuId; }
    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}

// Open a menu. Size MUST be a multiple of 9, 9..54.
public void openShop(Player player) {
    MenuHolder holder = new MenuHolder("shop");
    Inventory inv = Bukkit.createInventory(holder, 27, "§1Shop");
    holder.setInventory(inv);
    inv.setItem(13, someIcon);
    player.openInventory(inv);
}
```

Handle clicks in a `Listener`. Cancel first to prevent item theft/duplication, then route:
```java
@EventHandler
public void onClick(InventoryClickEvent e) {
    if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return; // not our GUI
    e.setCancelled(true);                                  // ALWAYS for menus
    if (e.getClickedInventory() == null) return;           // clicked outside
    int slot = e.getRawSlot();
    Player player = (Player) e.getWhoClicked();
    switch (holder.getMenuId()) {
        case "shop" -> handleShopClick(player, slot, e.getCurrentItem());
        default -> {}
    }
}
```

Notes:
- Import `org.bukkit.inventory.InventoryHolder`, `org.bukkit.event.inventory.InventoryClickEvent`.
- Inventory titles live on `InventoryView` (`e.getView().getTitle()`), never on `Inventory`.
- Register the listener in `onEnable()`: `getServer().getPluginManager().registerEvents(new MenuListener(this), this);`
