package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavacErrorFormatterTest {

    private final JavacErrorFormatter formatter = new JavacErrorFormatter();

    @Test
    void format_emptyList_returnsEmptyString() {
        assertThat(formatter.format(List.of())).isEmpty();
        assertThat(formatter.format(null)).isEmpty();
    }

    @Test
    void format_signatureMismatch_includesRequiredFoundAndHint() {
        JavacError err = new JavacError(
                "/work/GameSession.java", 103, 21,
                JavacError.Kind.SIGNATURE_MISMATCH,
                "constructor GameBoardGUI in class ... cannot be applied to given types;",
                "TicTacToe,Game,Player,char",
                "TicTacToe,GameSession,Player",
                "actual and formal argument lists differ in length",
                null, null, null, null, "raw"
        );

        String out = formatter.format(List.of(err));
        assertThat(out)
                .contains("SIGNATURE_MISMATCH")
                .contains("GameSession.java:103")
                .contains("Required: TicTacToe,Game,Player,char")
                .contains("Found:    TicTacToe,GameSession,Player")
                .contains("Reason:   actual and formal argument lists differ in length")
                .contains("Hint:");
    }

    @Test
    void format_cannotFindSymbol_inventoryGetTitle_emitsSpecificHint() {
        // The Bukkit-specific hint is the whole point — when the model hits
        // this same hallucination next time, the recovery prompt should tell
        // it exactly what to do instead of forcing a third blind retry.
        JavacError err = new JavacError(
                "/work/MainMenuGUI.java", 103, 74,
                JavacError.Kind.CANNOT_FIND_SYMBOL,
                "cannot find symbol",
                null, null, null,
                "method getTitle()",
                "variable inventory of type org.bukkit.inventory.Inventory",
                null, null, "raw"
        );

        String out = formatter.format(List.of(err));
        assertThat(out)
                .contains("CANNOT_FIND_SYMBOL")
                .contains("Symbol:   method getTitle()")
                .contains("Inventory has no getTitle()")
                .contains("InventoryView")
                .contains("event.getView().getTitle()");
    }

    @Test
    void format_incompatibleTypes_suggestsExtendsOrTypeChange() {
        JavacError err = new JavacError(
                "/work/GameSession.java", 230, 45,
                JavacError.Kind.INCOMPATIBLE_TYPES,
                "incompatible types: com.foo.GameSession cannot be converted to com.foo.Game",
                null, null, null, null, null,
                "com.foo.GameSession", "com.foo.Game", "raw"
        );

        String out = formatter.format(List.of(err));
        assertThat(out)
                .contains("INCOMPATIBLE_TYPES")
                .contains("Cannot convert com.foo.GameSession -> com.foo.Game")
                .contains("extends Game")
                .contains("implements Game");
    }

    @Test
    void format_capsAtTenErrors() {
        // Past 10 errors the LLM tends to lose focus. Verify the cap and
        // that the truncation note tells the model so.
        List<JavacError> errs = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            errs.add(new JavacError(
                    "/work/F" + i + ".java", i, 1,
                    JavacError.Kind.OTHER, "boom " + i,
                    null, null, null, null, null, null, null, "raw"
            ));
        }

        String out = formatter.format(errs);
        // First 10 are present, the 11th-15th are summarized
        assertThat(out)
                .contains("F0.java")
                .contains("F9.java")
                .doesNotContain("F10.java:")
                .contains("5 more errors omitted");
    }

    @Test
    void format_alwaysIncludesRecoveryDirective() {
        JavacError err = new JavacError(
                "/work/Foo.java", 1, 1,
                JavacError.Kind.OTHER, "something",
                null, null, null, null, null, null, null, "raw"
        );

        String out = formatter.format(List.of(err));
        assertThat(out)
                .contains("Fix every error listed above")
                .contains("Do not introduce new features");
    }
}
