package ru.ivanova.diplom.logistics.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ivanova.diplom.logistics.model.Order;
import ru.ivanova.diplom.logistics.model.Parameters;
import ru.ivanova.diplom.logistics.service.OptimizationService;
import ru.ivanova.diplom.logistics.service.StaticModelService;
import ru.ivanova.diplom.logistics.utils.JsonUtils;

import java.util.List;

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

            // Проверка наличия ключей geoJson и couriers
            if (jsonObject.has("geoJson") && jsonObject.has("parameters") && jsonObject.has("orders")) {
                JSONObject geoJsonObject = jsonObject.getJSONObject("geoJson");
                JSONObject paramsJson = jsonObject.getJSONObject("parameters");
                Parameters params = new Parameters(
                        paramsJson.getDouble("fuelRateMobStorage"),
                        paramsJson.getDouble("fuelRateCourierCar"),
                        paramsJson.getDouble("fuelCost"),
                        paramsJson.getDouble("mobStorageRate"),
                        paramsJson.getDouble("courierCarRate"),
                        paramsJson.getDouble("driverSalary"),
                        paramsJson.getInt("maxCountCouriers"),
                        paramsJson.getDouble("courierSalary"),
                        paramsJson.getDouble("courierScooterRate"),
                        paramsJson.getDouble("energyConsumption"),
                        paramsJson.getDouble("energyConsumptionCost"),
                        paramsJson.getDouble("maxCourierCarCapacity"),
                        paramsJson.getDouble("maxDeliveryCapacity"),
                        paramsJson.getDouble("maxTime"),
                        paramsJson.getDouble("orderProcessingTime"),
                        paramsJson.getDouble("courierScooterSpeed"),
                        paramsJson.getDouble("mobStorageSpeed")
                );
                JSONArray ordersArray = jsonObject.getJSONArray("orders");

                // Обработка данных динамической модели
                JSONObject dynamicModelGeoJson = optimizationService.optimizeRoute(geoJsonObject, params);

                //Обработка данных статической модели
                JSONObject staticModelGeoJson = staticModelService
                        .optimizeCourierRoutes(geoJsonObject, params, ordersArray);

                return ResponseEntity.ok(dynamicModelGeoJson.toString());
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
