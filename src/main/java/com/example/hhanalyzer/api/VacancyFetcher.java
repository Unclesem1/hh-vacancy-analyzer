package com.example.hhanalyzer.api;


import com.example.hhanalyzer.auth.AuthManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.httpackage com.example.hhanalyzer;

import com.example.hhanalyzer.auth.AuthManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VacancyFetcher {

    // Получить все вакансии по параметрам
    public static List<JsonNode> fetchAllVacancies(String text, String area, boolean remote, boolean noAgency) throws Exception {
        AuthManager auth = new AuthManager();
        String accessToken = auth.getAccessToken();

        List<JsonNode> allVacancies = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        int page = 0;
        int perPage = 100; // максимум, что даёт hh.ru
        int pages = 1;

        while (page < pages) {
            StringBuilder url = new StringBuilder("https://api.hh.ru/vacancies?");
            url.append("text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            if (area != null && !area.isBlank()) {
                url.append("&area=").append(URLEncoder.encode(area, StandardCharsets.UTF_8));
            }
            url.append("&per_page=").append(perPage);
            url.append("&page=").append(page);
            url.append("&only_with_vacancies=true");
            if (remote) url.append("&schedule=remote");
            if (noAgency) url.append("&no_agency=true");

            // Можно добавить: url.append("&search_field=name"); // поиск только по названию

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", "HH-API-Analyzer")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Ошибка при запросе вакансий: " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            if (root.has("items")) {
                for (JsonNode vacancy : root.get("items")) {
                    allVacancies.add(vacancy);
                }
            }

            // Узнаём количество страниц, чтобы пройти все (API возвращает поле 'pages')
            if (page == 0 && root.has("pages")) {
                pages = root.get("pages").asInt();
            }

            // Ограничение HH — максимум 2000 вакансий на запрос (20 страниц по 100)
            if (page >= 19) break;

            page++;
        }

        return allVacancies;
    }

    // Пример main для теста
    public static void main(String[] args) throws Exception {
        List<JsonNode> vacancies = fetchAllVacancies(
                "системный аналитик", // текст поиска
                "1",                  // регион "1" — Москва
                true,                 // только удалёнка
                true                  // без кадровых агентств
        );
        System.out.println("Всего найдено: " + vacancies.size());
        for (int i = 0; i < Math.min(vacancies.size(), 5); i++) {
            JsonNode v = vacancies.get(i);
            System.out.println((i+1) + ". " + v.get("name").asText() +
                    " | " + v.get("employer").get("name").asText() +
                    " | " + v.get("alternate_url").asText());
        }
    }
}
p.HttpResponse;

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


