package com.bekololek.pluginfactory.auth;

import com.bekololek.pluginfactory.auth.dto.DiscordTokenResponse;
import com.bekololek.pluginfactory.auth.dto.DiscordUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiscordOAuthService {

    private static final String DISCORD_AUTH_URL = "https://discord.com/api/oauth2/authorize";
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String DISCORD_USER_URL = "https://discord.com/api/users/@me";
    private static final long STATE_EXPIRY_MS = 600_000; // 10 minutes

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestTemplate restTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Long> validStates = new ConcurrentHashMap<>();

    public DiscordOAuthService(
            @Value("${discord.client-id}") String clientId,
            @Value("${discord.client-secret}") String clientSecret,
            @Value("${discord.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restTemplate = new RestTemplate();
    }

    public String buildAuthorizationUrl() {
        String state = generateState();
        return DISCORD_AUTH_URL
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=identify%20email"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    public void validateState(String state) {
        Long createdAt = validStates.remove(state);
        if (createdAt == null || System.currentTimeMillis() - createdAt > STATE_EXPIRY_MS) {
            throw new IllegalArgumentException("Invalid or expired OAuth state parameter");
        }
        // Clean up expired states
        long now = System.currentTimeMillis();
        validStates.entrySet().removeIf(e -> now - e.getValue() > STATE_EXPIRY_MS);
    }

    public DiscordTokenResponse exchangeCode(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<DiscordTokenResponse> response = restTemplate.postForEntity(
                DISCORD_TOKEN_URL, request, DiscordTokenResponse.class);

        return response.getBody();
    }

    public DiscordUserInfo fetchDiscordUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<DiscordUserInfo> response = restTemplate.exchange(
                DISCORD_USER_URL, HttpMethod.GET, request, DiscordUserInfo.class);

        return response.getBody();
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        validStates.put(state, System.currentTimeMillis());
        return state;
    }
}
