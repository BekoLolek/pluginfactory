package com.bekololek.pluginfactory.plan;

import com.bekololek.pluginfactory.common.BaseEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plan_documents")
@Getter
@Setter
@NoArgsConstructor
public class PlanDocument extends BaseEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "plugin_name")
    private String pluginName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "minecraft_version")
    private String minecraftVersion;

    @Column(name = "server_type")
    private String serverType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commands = "[]";

    @Column(name = "event_listeners", nullable = false, columnDefinition = "TEXT")
    private String eventListeners = "[]";

    @Column(name = "config_schema", nullable = false, columnDefinition = "TEXT")
    private String configSchema = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dependencies = "[]";

    @Column(name = "test_scenarios", nullable = false, columnDefinition = "TEXT")
    private String testScenarios = "[]";

    @Column(name = "estimated_loc")
    private Integer estimatedLoc;

    @Column(name = "complexity_score")
    private Integer complexityScore;

    @Column(nullable = false)
    private int version = 1;

    public int getCommandCount() {
        return parseJsonArraySize(commands);
    }

    public int getEventListenerCount() {
        return parseJsonArraySize(eventListeners);
    }

    public int getConfigEntryCount() {
        return parseJsonArraySize(configSchema);
    }

    public int getDependencyCount() {
        return parseJsonArraySize(dependencies);
    }

    public int getEstimatedLoc() {
        return estimatedLoc != null ? estimatedLoc : 0;
    }

    private int parseJsonArraySize(String json) {
        try {
            List<?> list = MAPPER.readValue(json, new TypeReference<List<?>>() {});
            return list.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
