USE WHEN: the plugin defines custom/named items, weapons, or tools and must reliably identify them later (clicks, crafts, drops) without relying on display name or lore.

### Custom item identification with PersistentDataContainer (PDC)

Tag items with a PersistentDataContainer key instead of matching on name/lore (which players can fake or anvils can change). The tag survives drops, inventory moves, and server restarts.

```java
// One shared key per plugin instance. NamespacedKey(plugin, "id") — lowercase, no spaces.
private final NamespacedKey idKey = new NamespacedKey(plugin, "weapon_id");

// Create a tagged item.
public ItemStack createWeapon(String id, String displayName, Material material, List<String> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();              // never null for normal materials
    meta.setDisplayName(displayName);                 // legacy String API is fine for compat
    meta.setLore(lore);
    meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
    item.setItemMeta(meta);                            // ALWAYS write meta back
    return item;
}

// Read the tag (null-safe for air/empty/untagged items).
public String getWeaponId(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    return item.getItemMeta().getPersistentDataContainer()
            .get(idKey, PersistentDataType.STRING);   // null if not one of ours
}

public boolean isCustomWeapon(ItemStack item) {
    return getWeaponId(item) != null;
}
```

Notes:
- Import `org.bukkit.NamespacedKey`, `org.bukkit.persistence.PersistentDataType`, `org.bukkit.inventory.meta.ItemMeta`.
- A unique PDC tag also prevents duplication exploits: validate `isCustomWeapon(...)` before applying custom behavior.
- `PersistentDataType` also offers INTEGER, DOUBLE, BYTE, etc. Use STRING for ids.
