package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternLibraryServiceTest {

    private final PatternLibraryService service = new PatternLibraryService();

    @Test
    void selectsRecipeAndPdcPatternsForACustomItemPlugin() {
        PlanDocument plan = new PlanDocument();
        plan.setPluginName("CustomWeapons");
        plan.setDescription("Custom weapons with hard crafting recipes and a PersistentDataContainer NBT tag.");
        plan.setCommands("[{\"name\":\"weapons\"}]");
        plan.setEventListeners("[{\"event\":\"CraftItemEvent\"}]");
        plan.setConfigSchema("[{\"key\":\"weapons\"}]");
        plan.setDependencies("[]");

        String out = service.selectPatterns(plan);

        assertThat(out)
                .contains("Verified patterns for this plugin")
                .contains("PersistentDataContainer")   // custom-items-pdc
                .contains("ShapedRecipe")               // recipes
                .contains("onTabComplete")              // commands (structural)
                .contains("registerEvents")             // events (structural)
                .contains("saveDefaultConfig");         // config (structural)
    }

    @Test
    void selectsGuiAndVaultForAnEconomyShop() {
        PlanDocument plan = new PlanDocument();
        plan.setDescription("A shop GUI menu where players buy items with Vault economy money.");
        plan.setCommands("[]");
        plan.setEventListeners("[]");
        plan.setConfigSchema("[]");
        plan.setDependencies("[\"Vault\"]");

        String out = service.selectPatterns(plan);

        assertThat(out)
                .contains("InventoryHolder")            // gui-menus
                .contains("net.milkbowl.vault");        // vault-economy
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        PlanDocument plan = new PlanDocument();
        plan.setDescription("does nothing in particular");
        plan.setCommands("[]");
        plan.setEventListeners("[]");
        plan.setConfigSchema("[]");
        plan.setDependencies("[]");
        plan.setViabilityStatus("READY");

        assertThat(service.selectPatterns(plan)).isEmpty();
    }
}
