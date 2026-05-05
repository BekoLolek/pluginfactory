package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavacErrorParserTest {

    private final JavacErrorParser parser = new JavacErrorParser();

    @Test
    void parse_nullOrBlank_returnsEmpty() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   \n  ")).isEmpty();
    }

    @Test
    void parse_signatureMismatch_extractsRequiredFoundReason() {
        // Real-shape Maven block from the failing tictactoe build the user reported.
        String raw = """
                [ERROR] COMPILATION ERROR :
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/game/GameSession.java:[103,21] constructor GameBoardGUI in class com.bekololek.generated.gui.GameBoardGUI cannot be applied to given types;
                  required: com.bekololek.generated.TicTacToe,com.bekololek.generated.game.Game,org.bukkit.entity.Player,char
                  found:    com.bekololek.generated.TicTacToe,com.bekololek.generated.game.GameSession,org.bukkit.entity.Player
                  reason: actual and formal argument lists differ in length
                [ERROR] -> [Help 1]
                """;

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        JavacError e = errors.get(0);
        assertThat(e.kind()).isEqualTo(JavacError.Kind.SIGNATURE_MISMATCH);
        assertThat(e.fileName()).isEqualTo("GameSession.java");
        assertThat(e.line()).isEqualTo(103);
        assertThat(e.column()).isEqualTo(21);
        assertThat(e.required())
                .contains("com.bekololek.generated.game.Game")
                .contains("char");
        assertThat(e.found()).contains("com.bekololek.generated.game.GameSession");
        assertThat(e.reason()).contains("differ in length");
    }

    @Test
    void parse_incompatibleTypes_extractsSourceAndTarget() {
        String raw = """
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/game/GameSession.java:[230,45] incompatible types: com.bekololek.generated.game.GameSession cannot be converted to com.bekololek.generated.game.Game
                """;

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        JavacError e = errors.get(0);
        assertThat(e.kind()).isEqualTo(JavacError.Kind.INCOMPATIBLE_TYPES);
        assertThat(e.line()).isEqualTo(230);
        assertThat(e.sourceType()).isEqualTo("com.bekololek.generated.game.GameSession");
        assertThat(e.targetType()).isEqualTo("com.bekololek.generated.game.Game");
    }

    @Test
    void parse_cannotFindSymbol_extractsSymbolAndLocation() {
        String raw = """
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/gui/MainMenuGUI.java:[103,74] cannot find symbol
                  symbol:   method getTitle()
                  location: variable inventory of type org.bukkit.inventory.Inventory
                """;

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        JavacError e = errors.get(0);
        assertThat(e.kind()).isEqualTo(JavacError.Kind.CANNOT_FIND_SYMBOL);
        assertThat(e.fileName()).isEqualTo("MainMenuGUI.java");
        assertThat(e.symbol()).isEqualTo("method getTitle()");
        assertThat(e.location()).contains("org.bukkit.inventory.Inventory");
    }

    @Test
    void parse_dedupesDuplicateErrors() {
        // Maven prints the same error twice — once in COMPILATION ERROR and
        // again with [ERROR] prefix in the failure summary. Only one should
        // reach the recovery prompt.
        String raw = """
                [ERROR] COMPILATION ERROR :
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/game/GameSession.java:[103,21] constructor GameBoardGUI in class com.bekololek.generated.gui.GameBoardGUI cannot be applied to given types;
                  required: com.bekololek.generated.TicTacToe,com.bekololek.generated.game.Game,org.bukkit.entity.Player,char
                  found:    com.bekololek.generated.TicTacToe,com.bekololek.generated.game.GameSession,org.bukkit.entity.Player
                  reason: actual and formal argument lists differ in length
                [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile (default-compile) on project tictactoe: Compilation failure: Compilation failure:
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/game/GameSession.java:[103,21] constructor GameBoardGUI in class com.bekololek.generated.gui.GameBoardGUI cannot be applied to given types;
                [ERROR]   required: com.bekololek.generated.TicTacToe,com.bekololek.generated.game.Game,org.bukkit.entity.Player,char
                [ERROR]   found:    com.bekololek.generated.TicTacToe,com.bekololek.generated.game.GameSession,org.bukkit.entity.Player
                [ERROR]   reason: actual and formal argument lists differ in length
                [ERROR] -> [Help 1]
                """;

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).kind()).isEqualTo(JavacError.Kind.SIGNATURE_MISMATCH);
    }

    @Test
    void parse_multipleErrors_inOrder() {
        // Mix of three errors as they would appear in a real failed build.
        String raw = """
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/gui/MainMenuGUI.java:[103,74] cannot find symbol
                  symbol:   method getTitle()
                  location: variable inventory of type org.bukkit.inventory.Inventory
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/gui/GameBoardGUI.java:[146,33] cannot find symbol
                  symbol:   method getTitle()
                  location: variable inventory of type org.bukkit.inventory.Inventory
                [ERROR] /plugin-workspace/src/main/java/com/bekololek/generated/game/GameSession.java:[230,45] incompatible types: com.bekololek.generated.game.GameSession cannot be converted to com.bekololek.generated.game.Game
                """;

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(3);
        assertThat(errors.get(0).fileName()).isEqualTo("MainMenuGUI.java");
        assertThat(errors.get(1).fileName()).isEqualTo("GameBoardGUI.java");
        assertThat(errors.get(2).fileName()).isEqualTo("GameSession.java");
    }

    @Test
    void parse_stripsAnsiColorCodes() {
        // Maven sometimes emits color codes when stderr is captured before
        // a TTY check disables them. ESC + "[1;31m" framing must not break
        // the path/line parser.
        String raw = "[1;31m[ERROR][m /work/Foo.java:[10,5] cannot find symbol\n"
                + "  symbol:   class Bar\n";

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).fileName()).isEqualTo("Foo.java");
        assertThat(errors.get(0).line()).isEqualTo(10);
        assertThat(errors.get(0).symbol()).isEqualTo("class Bar");
    }

    @Test
    void parse_stripsAnsiCodesEvenWithoutEscByte() {
        // Some pipes drop the ESC byte itself, leaving "[1;31m" as visible
        // text in the stored error. We should still parse cleanly.
        String raw = "[1;31m[ERROR][m /work/Foo.java:[10,5] cannot find symbol\n"
                + "  symbol:   class Bar\n";

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).fileName()).isEqualTo("Foo.java");
    }

    @Test
    void parse_nonCompilationOutput_returnsEmpty() {
        // Pre-Maven failures (e.g. dependency resolution, OOM) shouldn't
        // produce phantom JavacErrors. Recovery falls back to raw stderr
        // when the parsed list is empty.
        String raw = """
                [INFO] Scanning for projects...
                [INFO] Building tictactoe 1.0.0
                [ERROR] Failed to execute goal on project tictactoe: Could not resolve dependencies
                """;

        assertThat(parser.parse(raw)).isEmpty();
    }

    @Test
    void parse_handlesCompilationExceptionWrapper() {
        // The full message stored in BuildError.message has a wrapper prefix
        // from BuildPipelineService: "Maven build failed (exit code 1): ...".
        // Parser must not be confused by that prefix.
        String raw = "Maven build failed (exit code 1): [ERROR] COMPILATION ERROR :\n"
                + "[ERROR] /work/Foo.java:[1,1] cannot find symbol\n"
                + "  symbol:   class Missing\n";

        List<JavacError> errors = parser.parse(raw);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).fileName()).isEqualTo("Foo.java");
        assertThat(errors.get(0).symbol()).isEqualTo("class Missing");
    }
}
