package com.example.hhanalyzer.api;

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

    /**
     * Получить все id вакансий по заданным параметрам поиска.
     * @param text поисковый запрос (например, "системный аналитик")
     * @param area регион (например, "1" — Москва)
     * @param remote true — только удалёнка, false — любые
     * @param noAgency true — без кадровых агентств
     * @return список id вакансий (строки)
     */
    public static List<String> fetchVacancyIds(String text, String area, boolean remote, boolean noAgency) throws Exception {
        AuthManager auth = new AuthManager();
        String accessToken = auth.getAccessToken();

        List<String> allIds = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        int page = 0;
        int perPage = 100;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", "HH-API-Analyzer")
                    .GET()
                    .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                   // System.out.println("URL запроса: " + url.toString());
                  //  System.out.println("Код ответа: " + response.statusCode());
                   // System.out.println("Ответ сервера: " + response.body());
                    

            if (response.statusCode() != 200) {
                throw new IOException("Ошибка при запросе вакансий: " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            if (root.has("items")) {
                for (JsonNode vacancy : root.get("items")) {
                    allIds.add(vacancy.get("id").asText());
                }
            }

            // Узнаём количество страниц (API возвращает поле 'pages')
            if (page == 0 && root.has("pages")) {
                pages = root.get("pages").asInt();
            }
            // Ограничение hh.ru: максимум 2000 вакансий на запрос (20 страниц по 100)
            if (page >= 19) break;
            page++;
        }
        return allIds;
    }

    // Для теста: пример main
    public static void main(String[] args) throws Exception {
        List<String> ids = fetchVacancyIds(
                "системный аналитик", // текст поиска
                "1",                  // регион "1" — Москва
                true,                 // только удалёнка
                true                  // без кадровых агентств
        );
        System.out.println("Найдено id вакансий: " + ids.size());
        for (int i = 0; i <  ids.size(); i++) {
        System.out.println((i + 1) + ": " + ids.get(i));
        }
    }
}

