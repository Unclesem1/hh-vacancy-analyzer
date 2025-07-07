package com.example.hhanalyzer.api;


import com.example.hhanalyzer.auth.AuthManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiTest {
    public static void main(String[] args) throws Exception {
        AuthManager auth = new AuthManager();
        String token = auth.getAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.hh.ru/me"))
            .header("Authorization", "Bearer " + token)
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Ответ от hh.ru:");
        System.out.println(response.body());
    }
}

