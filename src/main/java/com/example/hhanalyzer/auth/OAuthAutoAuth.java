package com.example.hhanalyzer.auth;

import com.example.hhanalyzer.MiniServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class OAuthAutoAuth {

    // Укажи свои client_id, secret и redirect_uri
    private static final String CLIENT_ID = "KA2013FOJ7UU70FP53UQGLSCFU2O1367EHU6R5NU7LIEDM3JRH04UBNAFGQTJVU1";
    private static final String CLIENT_SECRET = "U7FK881IHG474ABK882QD8GK2CHA47TEO07P3RAMBUR74CCPFO3F2GDO7IH9J3RD";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final int PORT = 8080;

    private static String accessToken;
    private static String refreshToken;

    public static void main(String[] args) throws Exception {
        // 1. Запускаем mini-HTTP сервер для перехвата code
        CountDownLatch waitForCode = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> runCallbackServer(waitForCode));
        serverThread.start();

        // 2. Открываем браузер с ссылкой на hh.ru OAuth
        String authUrl = String.format(
            "https://hh.ru/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=xyz",
            CLIENT_ID, URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8));
        System.out.println("Открой ссылку (или она сама откроется):\n" + authUrl);

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        }

        // 3. Ждём пока сервер получит code
        waitForCode.await();

        // 4. Теперь accessToken получен, можно использовать!
        System.out.println("access_token: " + accessToken);
        System.out.println("refresh_token: " + refreshToken);

        // ...Теперь можно делать API-запросы с Authorization: Bearer accessToken

        System.exit(0); // завершить процесс (чтобы закрыть сервер)
    }

    // Mini-HTTP сервер, ловит code с hh.ru и меняет его на токен
    private static void runCallbackServer(CountDownLatch waitForCode) {
        try (var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(PORT), 0)) {
            MiniServer.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                for (String param : query.split("&")) {
                    if (param.startsWith("code=")) {
                        code = param.substring("code=".length());
                        break;
                    }
                }
                String response;
                if (code != null) {
                    response = "<html><body>Авторизация прошла успешно! Можешь закрыть это окно.</body></html>";
                    exchange.sendResponseHeaders(200, response.length());
                    // Меняем code на access_token
                    exchangeCodeForToken(code);
                    waitForCode.countDown();
                } else {
                    response = "Нет code!";
                    exchange.sendResponseHeaders(400, response.length());
                }
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            MiniServer.start();
            System.out.println("Ожидание авторизации на http://localhost:" + PORT + "/callback ...");
            while (waitForCode.getCount() > 0) {
                Thread.sleep(100);
            }
            MiniServer.stop(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Меняет code на access_token через hh.ru
    private static void exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String url = "https://hh.ru/oauth/token";
        String body = "grant_type=authorization_code"
                + "&client_id=" + CLIENT_ID
                + "&client_secret=" + CLIENT_SECRET
                + "&code=" + code
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Парсим JSON и сохраняем токены
            ObjectMapper om = new ObjectMapper();
            JsonNode tree = om.readTree(response.body());
            accessToken = tree.get("access_token").asText();
            refreshToken = tree.has("refresh_token") ? tree.get("refresh_token").asText() : null;
        } else {
            throw new RuntimeException("Ошибка авторизации: " + response.body());
        }
    }
}
