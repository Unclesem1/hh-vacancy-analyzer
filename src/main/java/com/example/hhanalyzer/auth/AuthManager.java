package com.example.hhanalyzer.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Instant;



public class AuthManager {

    public static final String CLIENT_ID = "KA2013FOJ7UU70FP53UQGLSCFU2O1367EHU6R5NU7LIEDM3JRH04UBNAFGQTJVU1";
    public static final String CLIENT_SECRET = "U7FK881IHG474ABK882QD8GK2CHA47TEO07P3RAMBUR74CCPFO3F2GDO7IH9J3RD";
    public static final String REDIRECT_URI = "http://localhost:8080/callback";
    public static final String TOKEN_FILE = "token.json";
    private static final ObjectMapper om = new ObjectMapper();

    private String accessToken;
    private String refreshToken;
    private long expiresAt; // unix timestamp (секунды)

    public AuthManager() {
        loadTokens();
    }

    public String getAccessToken() throws Exception {
        if (isAccessTokenValid()) {
            return accessToken;
        } else if (refreshToken != null) {
            refreshAccessToken();
            return accessToken;
        } else {
            throw new IllegalStateException("Нет токена для авторизации! Сначала пройди OAuth через браузер.");
        }
    }

    public void setTokensFromOAuth(String access, String refresh, int expiresIn) throws IOException {
        this.accessToken = access;
        this.refreshToken = refresh;
        this.expiresAt = Instant.now().getEpochSecond() + expiresIn - 10; // запас 10 сек
        saveTokens();
    }

    private void refreshAccessToken() throws Exception {
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + CLIENT_ID
                + "&client_secret=" + CLIENT_SECRET;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://hh.ru/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode node = om.readTree(response.body());
            this.accessToken = node.get("access_token").asText();
            if (node.has("refresh_token")) {
                this.refreshToken = node.get("refresh_token").asText();
            }
            this.expiresAt = Instant.now().getEpochSecond() + node.get("expires_in").asInt() - 10;
            saveTokens();
            System.out.println("Токен обновлен по refresh_token.");
        } else {
            throw new RuntimeException("Ошибка обновления токена: " + response.body());
        }
    }

    private boolean isAccessTokenValid() {
        return accessToken != null && expiresAt > Instant.now().getEpochSecond() + 10;
    }

    private void loadTokens() {
        try {
            if (Files.exists(Path.of(TOKEN_FILE))) {
                JsonNode node = om.readTree(new File(TOKEN_FILE));
                this.accessToken = node.get("access_token").asText();
                this.refreshToken = node.get("refresh_token").asText();
                this.expiresAt = node.get("expires_at").asLong();
            }
        } catch (Exception e) {
            // файл поврежден — игнорируем, придется снова авторизоваться
        }
    }

    private void saveTokens() throws IOException {
        String json = String.format("{\"access_token\":\"%s\",\"refresh_token\":\"%s\",\"expires_at\":%d}",
                accessToken, refreshToken, expiresAt);
        Files.writeString(Path.of(TOKEN_FILE), json, StandardCharsets.UTF_8);
    }

    public String getRefreshToken() { return refreshToken; }
    public long getExpiresAt() { return expiresAt; }
}
