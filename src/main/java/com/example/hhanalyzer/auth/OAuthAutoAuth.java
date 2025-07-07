package com.example.hhanalyzer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import com.sun.net.httpserver.HttpServer;

public class OAuthAutoAuth {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        AuthManager manager = new AuthManager();
        try {
            String token = manager.getAccessToken();
            System.out.println("Токен актуален: " + token);
            return;
        } catch (Exception e) {
            System.out.println("Нужна авторизация через браузер...");
        }

        // Запускаем mini-HTTP сервер для получения кода
        CountDownLatch waitForCode = new CountDownLatch(1);
        final String[] codeHolder = new String[1];
        final int[] expiresInHolder = new int[1];
        final String[] accessTokenHolder = new String[1];
        final String[] refreshTokenHolder = new String[1];

        Thread serverThread = new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    String code = null;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            if (param.startsWith("code=")) {
                                code = param.substring("code=".length());
                                break;
                            }
                        }
                    }
                    String responseText;
                    if (code != null) {
                        responseText = "<html><body>Авторизация прошла успешно! Можешь закрыть это окно.</body></html>";
                        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                        try {
                            var result = exchangeCodeForToken(code);
                            accessTokenHolder[0] = result[0];
                            refreshTokenHolder[0] = result[1];
                            expiresInHolder[0] = Integer.parseInt(result[2]);
                            codeHolder[0] = code;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        waitForCode.countDown();
                    } else {
                        responseText = "Нет code!";
                        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                        exchange.sendResponseHeaders(400, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    }
                });
                server.start();
                System.out.println("Ожидание авторизации на http://localhost:" + PORT + "/callback ...");
                while (waitForCode.getCount() > 0) {
                    Thread.sleep(100);
                }
                server.stop(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Открываем браузер для авторизации
        String authUrl = String.format(
                "https://hh.ru/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=xyz",
                AuthManager.CLIENT_ID, URLEncoder.encode(AuthManager.REDIRECT_URI, StandardCharsets.UTF_8));
        System.out.println("Открой ссылку (или она откроется сама):\n" + authUrl);

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        }

        waitForCode.await();

        // Сохраняем токены
        manager.setTokensFromOAuth(accessTokenHolder[0], refreshTokenHolder[0], expiresInHolder[0]);
        System.out.println("Токены получены и сохранены.");
    }

    // Обменять code на access_token, refresh_token, expires_in
    private static String[] exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String url = "https://hh.ru/oauth/token";
        String body = "grant_type=authorization_code"
                + "&client_id=" + AuthManager.CLIENT_ID
                + "&client_secret=" + AuthManager.CLIENT_SECRET
                + "&code=" + code
                + "&redirect_uri=" + URLEncoder.encode(AuthManager.REDIRECT_URI, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ObjectMapper om = new ObjectMapper();
            JsonNode tree = om.readTree(response.body());
            String accessToken = tree.get("access_token").asText();
            String refreshToken = tree.has("refresh_token") ? tree.get("refresh_token").asText() : null;
            int expiresIn = tree.has("expires_in") ? tree.get("expires_in").asInt() : 3600;
            return new String[]{accessToken, refreshToken, String.valueOf(expiresIn)};
        } else {
            throw new RuntimeException("Ошибка авторизации: " + response.body());
        }
    }
}
