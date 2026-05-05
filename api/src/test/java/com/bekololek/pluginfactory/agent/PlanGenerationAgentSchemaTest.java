package com.bekololek.pluginfactory.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan tool schema is the contract between the LLM and our generation
 * pipeline — accidentally dropping a property silently changes plan output.
 * Schema-shape regressions are easy to miss in code review, so we lock the
 * shape down here.
 */
class PlanGenerationAgentSchemaTest {

    @Test
    @SuppressWarnings("unchecked")
    void schema_declaresClassesProperty() {
        Map<String, Object> schema = PlanGenerationAgent.planToolSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(properties).containsKey("classes");
        Map<String, Object> classes = (Map<String, Object>) properties.get("classes");
        assertThat(classes).containsEntry("type", "array");

        Map<String, Object> classItem = (Map<String, Object>) classes.get("items");
        Map<String, Object> classProps = (Map<String, Object>) classItem.get("properties");
        assertThat(classProps)
                .containsKey("name")
                .containsKey("extends")
                .containsKey("implements")
                .containsKey("constructorParams");

        // `name` is the only class-level required field — extends/implements
        // are optional because most concrete classes don't have either.
        assertThat((List<String>) classItem.get("required")).containsExactly("name");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schema_classesNotInRequiredList() {
        // Trivial single-class plugins should still parse without classes.
        // Locking it as required would force every plan to ship the whole
        // class roster, even when there's only a JavaPlugin subclass.
        Map<String, Object> schema = PlanGenerationAgent.planToolSchema();
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).doesNotContain("classes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schema_namePatternIsSimpleClassName() {
        // Pattern validation prevents the model from emitting "List<Foo>" or
        // "com.bar.Baz" — those would break the implementer's contract lookup.
        Map<String, Object> schema = PlanGenerationAgent.planToolSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> classes = (Map<String, Object>) properties.get("classes");
        Map<String, Object> classItem = (Map<String, Object>) classes.get("items");
        Map<String, Object> classProps = (Map<String, Object>) classItem.get("properties");
        Map<String, Object> nameProp = (Map<String, Object>) classProps.get("name");

        assertThat((String) nameProp.get("pattern")).isEqualTo("^[A-Z][A-Za-z0-9_]*$");
    }
}
