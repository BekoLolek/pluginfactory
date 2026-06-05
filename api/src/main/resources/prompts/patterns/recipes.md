USE WHEN: the plugin registers custom crafting recipes (shaped or shapeless), especially for custom/PDC-tagged result items.

### Registering crafting recipes

Each recipe needs a unique `NamespacedKey`. Register in `onEnable()`; remove your keys on reload to avoid "duplicate recipe" errors.

```java
public void registerRecipes() {
    ItemStack result = createWeapon("void_reaper", "§5Void Reaper", Material.NETHERITE_SWORD, lore);
    NamespacedKey key = new NamespacedKey(plugin, "void_reaper");

    ShapedRecipe recipe = new ShapedRecipe(key, result);
    recipe.shape("ABA", "ANA", " S ");                 // 3 rows, 3 cols
    recipe.setIngredient('A', Material.NETHERITE_BLOCK);
    recipe.setIngredient('N', Material.NETHER_STAR);
    recipe.setIngredient('S', Material.BLAZE_ROD);
    Bukkit.addRecipe(recipe);                           // server-wide
}

// Shapeless variant:
// ShapelessRecipe r = new ShapelessRecipe(key, result);
// r.addIngredient(2, Material.DIAMOND); r.addIngredient(Material.STICK);
// Bukkit.addRecipe(r);
```

Reload safety — remove only your own recipes by key before re-registering:
```java
Bukkit.removeRecipe(new NamespacedKey(plugin, "void_reaper"));
```

Apply the custom result's PDC tag when crafted (the recipe result already carries the meta you built, but if you build the result fresh, re-tag it). To gate crafting by permission, listen for `PrepareItemCraftEvent` and clear the result; to finalize/validate, listen for `CraftItemEvent`:
```java
@EventHandler
public void onPrepare(PrepareItemCraftEvent e) {
    Recipe r = e.getRecipe();
    if (!(r instanceof Keyed keyed) || !keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return;
    if (e.getView().getPlayer() instanceof Player p && !p.hasPermission("customweapons.craft")) {
        e.getInventory().setResult(null);              // hide result if not allowed
    }
}
```

Notes: import `org.bukkit.inventory.ShapedRecipe`/`ShapelessRecipe`/`Recipe`, `org.bukkit.Keyed`, `org.bukkit.NamespacedKey`. Recipe keys are global — namespace them with your plugin to avoid clashes.
