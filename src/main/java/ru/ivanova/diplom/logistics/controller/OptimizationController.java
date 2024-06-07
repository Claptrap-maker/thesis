package ru.ivanova.diplom.logistics.controller;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ivanova.diplom.logistics.service.OptimizationService;

@RestController
@RequestMapping("/api/optimize")
public class OptimizationController {

    @Autowired
    private OptimizationService optimizationService;

    @PostMapping
    public ResponseEntity<String> optimize(@RequestBody String geoJson) {
        try {
            // Преобразование строки в JSON объект
            JSONObject jsonObject = new JSONObject(geoJson);

            // Проверка наличия ключей geoJson и couriers
            if (jsonObject.has("geoJson") && jsonObject.has("couriers")) {
                JSONObject geoJsonObject = jsonObject.getJSONObject("geoJson");
                int couriers = jsonObject.getInt("couriers");

                // Обработка данных в сервисе
                JSONObject optimizedGeoJson = optimizationService.optimizeRoute(geoJsonObject, couriers);

                return ResponseEntity.ok(optimizedGeoJson.toString());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Key 'geoJson' not found.");
            }
        } catch (JSONException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid JSON format.");
        }
    }

    @GetMapping("/hello")
    public String home() {
        return "Hello World!";
    }

}
