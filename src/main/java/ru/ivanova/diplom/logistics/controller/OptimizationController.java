package ru.ivanova.diplom.logistics.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ivanova.diplom.logistics.model.Parameters;
import ru.ivanova.diplom.logistics.service.OptimizationService;
import ru.ivanova.diplom.logistics.service.StaticModelService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/optimize")
public class OptimizationController {

    @Autowired
    private OptimizationService optimizationService;

    @Autowired
    private StaticModelService staticModelService;

    @PostMapping
    public ResponseEntity<String> optimize(@RequestBody String geoJson) {
        try {
            // Преобразование строки в JSON объект
            JSONObject jsonObject = new JSONObject(geoJson);

            // Проверка наличия ключей geo_json и couriers
            if (jsonObject.has("geo_json") && jsonObject.has("parameters") && jsonObject.has("orders")
                    && jsonObject.has("request_data")) {
                JSONObject requestDataJson = jsonObject.getJSONObject("request_data");
                JSONObject geoJsonObject = jsonObject.getJSONObject("geo_json");
                JSONObject paramsJson = jsonObject.getJSONObject("parameters");
                Parameters params = new Parameters(
                        paramsJson.getDouble("fuel_rate_mob_storage"),
                        paramsJson.getDouble("fuel_rate_courier_car"),
                        paramsJson.getDouble("fuel_cost"),
                        paramsJson.getDouble("mob_storage_rate"),
                        paramsJson.getDouble("courier_car_rate"),
                        paramsJson.getDouble("driver_salary"),
                        paramsJson.getInt("max_count_couriers"),
                        paramsJson.getDouble("courier_salary"),
                        paramsJson.getDouble("courier_scooter_rate"),
                        paramsJson.getDouble("energy_consumption"),
                        paramsJson.getDouble("energy_consumption_cost"),
                        paramsJson.getDouble("max_courier_car_capacity"),
                        paramsJson.getDouble("max_delivery_capacity"),
                        paramsJson.getDouble("max_time"),
                        paramsJson.getDouble("order_processing_time"),
                        paramsJson.getDouble("courier_scooter_speed"),
                        paramsJson.getDouble("mob_storage_speed")
                );
                JSONArray ordersArray = jsonObject.getJSONArray("orders");

                JSONObject dynamicModelGeoJson = new JSONObject();
                JSONObject staticModelGeoJson = new JSONObject();

                // Обработка данных динамической модели
                CompletableFuture<JSONObject> dynamicModelFuture = CompletableFuture.supplyAsync(
                        () -> optimizationService.optimizeRoute(geoJsonObject, params, requestDataJson));

                // Обработка данных статической модели
                CompletableFuture<JSONObject> staticModelFuture = CompletableFuture.supplyAsync(
                        () -> staticModelService.optimizeCourierRoutes(
                                geoJsonObject, params, ordersArray, requestDataJson));

                // Ожидание завершения обоих методов и получение результатов
                try {
                    dynamicModelGeoJson = dynamicModelFuture.get();
                    staticModelGeoJson = staticModelFuture.get();

                    // Дальнейшая обработка результатов
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }

                return ResponseEntity.ok("Данные в обработке");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("One of keys is invalid.");
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
