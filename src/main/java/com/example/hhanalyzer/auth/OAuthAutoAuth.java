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

    // CLIENT_ID, CLIENT_SECRET и REDIRECT_URI свои
    private static final String CLIENT_ID = "KA2013FOJ7UU70FP53UQGLSCFU2O1367EHU6R5NU7LIEDM3JRH04UBNAFGQTJVU1";
    private static final String CLIENT_SECRET = "U7FK881IHG474ABK882QD8GK2CHA47TEO07P3RAMBUR74CCPFO3F2GDO7IH9J3RD";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final int PORT = 8080;

    private static String accessToken;
    private static String refreshToken;

    public static void main(String[] args) throws Exception {
        CountDownLatch waitForCode = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> runCallbackServer(waitForCode));
        serverThread.start();

        String authUrl = String.format(
            "https://hh.ru/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=xyz",
            CLIENT_ID, URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8));
        System.out.println("Открой ссылку (или она откроется сама):\n" + authUrl);

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        }

        waitForCode.await();

        System.out.println("access_token: " + accessToken);
        System.out.println("refresh_token: " + refreshToken);

        System.exit(0);
    }

    private static void runCallbackServer(CountDownLatch waitForCode) {
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
                String response;
                if (code != null) {
                    response = "<html><body>Авторизация прошла успешно! Можешь закрыть это окно.</body></html>";
                    exchange.sendResponseHeaders(200, response.length());
                    try {
                        exchangeCodeForToken(code); // <- теперь обработка ошибок
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    waitForCode.countDown();
                } else {
                    response = "Нет code!";
                    exchange.sendResponseHeaders(400, response.length());
                }
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
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
    }

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
            ObjectMapper om = new ObjectMapper();
            JsonNode tree = om.readTree(response.body());
            accessToken = tree.get("access_token").asText();
            refreshToken = tree.has("refresh_token") ? tree.get("refresh_token").asText() : null;
        } else {
            throw new RuntimeException("Ошибка авторизации: " + response.body());
        }
    }
}
