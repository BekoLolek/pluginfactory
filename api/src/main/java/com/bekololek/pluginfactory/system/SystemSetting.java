package com.bekololek.pluginfactory.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A single global key/value setting (see {@link SystemSettingService}). */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 512)
    private String value;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
