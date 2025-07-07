package com.example.hhanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.example.hhanalyzer.auth.AuthManager;

public class VacancyDetailLoader {

    // Получить подробную вакансию по id
    public static JsonNode getVacancyDetail(String id) throws Exception {
        AuthManager auth = new AuthManager();
        String accessToken = auth.getAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hh.ru/vacancies/" + id))
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "HH-API-Analyzer")
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка получения вакансии " + id + ": " + response.body());
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(response.body());
    }

    // Главный метод: получить все id через fetch, а потом для каждого id выгрузить подробности
    public static void main(String[] args) throws Exception {
        // Получаем id вакансий через VacancyFetcher
        List<String> ids = VacancyFetcher.fetchVacancyIds(
                "системный аналитик", // поисковая строка
                "1",                  // Москва
                true,                 // только удалёнка
                true                  // без агентств
        );
        System.out.println("Всего найдено id: " + ids.size());

        ObjectMapper mapper = new ObjectMapper();

        // Выводим подробности по всем вакансиям (можно ограничить до первых N для теста)
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            JsonNode detail = getVacancyDetail(id);
            System.out.println("========== Вакансия " + (i + 1) + " ==========");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(detail));
            // Если хочешь сохранить в БД/файл — пиши здесь!
        }
    }
}
