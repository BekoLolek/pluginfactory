package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.user.dto.ApiKeyCreatedDto;
import com.bekololek.pluginfactory.user.dto.ApiKeyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ApiKeyCreatedDto createApiKey(UUID userId, String name) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawKey = "bpf_" + HexFormat.of().formatHex(randomBytes);
        String keyHash = sha256(rawKey);
        String lastFour = rawKey.substring(rawKey.length() - 4);

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setKeyHash(keyHash);
        apiKey.setName(name);
        apiKey.setLastFour(lastFour);
        apiKey = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedDto(
                apiKey.getId(),
                apiKey.getName(),
                rawKey,
                apiKey.getLastFour(),
                apiKey.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> listKeys(UUID userId) {
        return apiKeyRepository.findByUserIdAndRevokedFalse(userId).stream()
                .map(key -> new ApiKeyDto(
                        key.getId(),
                        key.getName(),
                        key.getLastFour(),
                        key.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void revokeKey(UUID userId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new NotFoundException("API key not found"));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
