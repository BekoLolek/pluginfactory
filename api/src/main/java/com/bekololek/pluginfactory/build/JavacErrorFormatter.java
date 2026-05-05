package com.bekololek.pluginfactory.build;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Formats a parsed {@link JavacError} list into the prompt block sent to the
 * LLM during build recovery. The goal is "what to fix, where, and a likely
 * cause" — the model is dramatically more reliable when it sees structured
 * errors with hints than when it sees raw Maven stderr.
 */
@Service
public class JavacErrorFormatter {

    /** Cap how many errors we forward — past 10, the LLM tends to lose focus. */
    private static final int MAX_ERRORS = 10;

    public String format(List<JavacError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The previous build failed with the following compilation errors:\n\n");

        int shown = Math.min(errors.size(), MAX_ERRORS);
        for (int i = 0; i < shown; i++) {
            JavacError e = errors.get(i);
            sb.append(i + 1).append(". ").append(e.kind()).append(" at ")
                    .append(e.fileName()).append(':').append(e.line()).append('\n');
            sb.append("   ").append(e.summary()).append('\n');

            switch (e.kind()) {
                case SIGNATURE_MISMATCH -> appendSignatureMismatch(sb, e);
                case INCOMPATIBLE_TYPES -> appendIncompatibleTypes(sb, e);
                case CANNOT_FIND_SYMBOL -> appendCannotFindSymbol(sb, e);
                default -> { /* summary line is sufficient */ }
            }
            sb.append('\n');
        }

        if (errors.size() > MAX_ERRORS) {
            sb.append("(... and ").append(errors.size() - MAX_ERRORS)
                    .append(" more errors omitted; fix the ones above first.)\n\n");
        }

        sb.append("Fix every error listed above. Make all the call sites and class ")
                .append("contracts agree. Do not introduce new features or change scope — ")
                .append("only repair what is broken.");
        return sb.toString();
    }

    private void appendSignatureMismatch(StringBuilder sb, JavacError e) {
        if (e.required() != null) {
            sb.append("   Required: ").append(e.required()).append('\n');
        }
        if (e.found() != null) {
            sb.append("   Found:    ").append(e.found()).append('\n');
        }
        if (e.reason() != null) {
            sb.append("   Reason:   ").append(e.reason()).append('\n');
        }
        sb.append("   Hint: the constructor or method signature does not match the call. ")
                .append("Either fix the call site to match the existing signature, or change ")
                .append("the signature to match what the caller passes — but every call site ")
                .append("must agree with the declaration.\n");
    }

    private void appendIncompatibleTypes(StringBuilder sb, JavacError e) {
        if (e.sourceType() != null && e.targetType() != null) {
            sb.append("   Cannot convert ").append(e.sourceType())
                    .append(" -> ").append(e.targetType()).append('\n');
            sb.append("   Hint: these types are unrelated as declared. If ")
                    .append(simpleName(e.sourceType()))
                    .append(" was supposed to be a kind of ")
                    .append(simpleName(e.targetType()))
                    .append(", declare it as `extends ")
                    .append(simpleName(e.targetType()))
                    .append("` or `implements ")
                    .append(simpleName(e.targetType()))
                    .append("`. Otherwise, change the parameter/variable type to ")
                    .append(simpleName(e.sourceType())).append(".\n");
        }
    }

    private void appendCannotFindSymbol(StringBuilder sb, JavacError e) {
        if (e.symbol() != null) {
            sb.append("   Symbol:   ").append(e.symbol()).append('\n');
        }
        if (e.location() != null) {
            sb.append("   Location: ").append(e.location()).append('\n');
        }
        String hint = bukkitApiHint(e);
        if (hint != null) {
            sb.append("   Hint: ").append(hint).append('\n');
        } else {
            sb.append("   Hint: this symbol does not exist where it is being called. ")
                    .append("Either you misspelled it, the type does not declare it, or you ")
                    .append("forgot to define the class/method/field in another file.\n");
        }
    }

    /** Pattern-matched callouts for the most common Bukkit API hallucinations. */
    private String bukkitApiHint(JavacError e) {
        String symbol = e.symbol() == null ? "" : e.symbol().toLowerCase(Locale.ROOT);
        String location = e.location() == null ? "" : e.location().toLowerCase(Locale.ROOT);

        if (symbol.contains("gettitle") && location.contains("inventory")
                && !location.contains("inventoryview")) {
            return "Inventory has no getTitle() method — the title lives on InventoryView. "
                    + "Inside an inventory event handler use `event.getView().getTitle()`. "
                    + "If you need it elsewhere, hold the InventoryView returned by "
                    + "`player.openInventory(...)` or store the title on your InventoryHolder.";
        }
        if (symbol.contains("getregisteredlisteners") && location.contains("pluginmanager")) {
            return "PluginManager has no getRegisteredListeners(). Use the static "
                    + "HandlerList.getRegisteredListeners(plugin) instead.";
        }
        return null;
    }

    private String simpleName(String fqcn) {
        if (fqcn == null) {
            return "?";
        }
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }
}
