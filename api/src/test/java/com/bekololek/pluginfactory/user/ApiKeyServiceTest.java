package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.user.dto.ApiKeyCreatedDto;
import com.bekololek.pluginfactory.user.dto.ApiKeyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    void createApiKey_success() {
        UUID userId = UUID.randomUUID();

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(Instant.now());
            return key;
        });

        ApiKeyCreatedDto result = apiKeyService.createApiKey(userId, "My API Key");

        assertThat(result.name()).isEqualTo("My API Key");
        assertThat(result.key()).startsWith("bpf_");
        assertThat(result.key()).hasSize(68); // "bpf_" (4) + 64 hex chars
        assertThat(result.lastFour()).hasSize(4);
        assertThat(result.id()).isNotNull();

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyHash()).isNotBlank();
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void listKeys_success() {
        UUID userId = UUID.randomUUID();

        ApiKey key1 = new ApiKey();
        key1.setId(UUID.randomUUID());
        key1.setName("Key 1");
        key1.setLastFour("abcd");
        key1.setCreatedAt(Instant.now());

        ApiKey key2 = new ApiKey();
        key2.setId(UUID.randomUUID());
        key2.setName("Key 2");
        key2.setLastFour("efgh");
        key2.setCreatedAt(Instant.now());

        when(apiKeyRepository.findByUserIdAndRevokedFalse(userId)).thenReturn(List.of(key1, key2));

        List<ApiKeyDto> result = apiKeyService.listKeys(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Key 1");
        assertThat(result.get(1).name()).isEqualTo("Key 2");
    }

    @Test
    void revokeKey_success() {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        apiKey.setId(keyId);
        apiKey.setUserId(userId);
        apiKey.setRevoked(false);

        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);

        apiKeyService.revokeKey(userId, keyId);

        assertThat(apiKey.isRevoked()).isTrue();
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void revokeKey_notFound() {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        when(apiKeyRepository.findByIdAndUserId(keyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeKey(userId, keyId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("API key not found");
    }
}
