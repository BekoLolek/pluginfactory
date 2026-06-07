package com.bekololek.pluginfactory.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository repository;

    private SystemSettingService service;

    private void init(String discordUrl) {
        service = new SystemSettingService(repository, discordUrl);
    }

    @Test
    void maintenanceDefaultsToFalseWhenUnset() {
        init("");
        when(repository.findById(SystemSettingService.MAINTENANCE_KEY)).thenReturn(Optional.empty());
        assertThat(service.isMaintenanceMode()).isFalse();
    }

    @Test
    void readsStoredMaintenanceFlag() {
        init("");
        SystemSetting s = new SystemSetting();
        s.setKey(SystemSettingService.MAINTENANCE_KEY);
        s.setValue("true");
        when(repository.findById(SystemSettingService.MAINTENANCE_KEY)).thenReturn(Optional.of(s));
        assertThat(service.isMaintenanceMode()).isTrue();
    }

    @Test
    void setMaintenancePersistsFlag() {
        init("");
        when(repository.findById(SystemSettingService.MAINTENANCE_KEY)).thenReturn(Optional.empty());
        lenient().when(repository.save(any(SystemSetting.class))).thenAnswer(i -> i.getArgument(0));

        service.setMaintenanceMode(true);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(s ->
                SystemSettingService.MAINTENANCE_KEY.equals(s.getKey())
                        && "true".equals(s.getValue())
                        && s.getUpdatedAt() != null));
    }

    @Test
    void discordUrlNeverNull() {
        init(null);
        assertThat(service.getDiscordUrl()).isEqualTo("");
    }
}
