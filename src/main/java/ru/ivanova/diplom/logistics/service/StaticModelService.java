package ru.ivanova.diplom.logistics.service;

import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ivanova.diplom.logistics.config.RabbitConfig;
import ru.ivanova.diplom.logistics.model.*;
import ru.ivanova.diplom.logistics.utils.JsonUtils;

import java.util.*;

@Service
public class StaticModelService {

    private final RabbitMQSender rabbitMQSender;

    @Autowired
    public StaticModelService(RabbitMQSender rabbitMQSender) {
        this.rabbitMQSender = rabbitMQSender;
    }

    public JSONObject optimizeCourierRoutes(JSONObject geoJson, Parameters params, JSONArray ordersArray,
                                            JSONObject requestDataJson) {
        try {
            List<DoublePoint> points = extractPointsFromGeoJson(geoJson);
            DoublePoint startPoint = points.remove(0); // Начальная точка - первый элемент

            //Получаем список заказов
            List<Order> orders = JsonUtils.convertJsonArrayToOrderList(ordersArray);

            // Связываем заказы с соответствующими пунктами выдачи
            int i = 0;
            for (Order order : orders) {
                if (order != null) {
                    order.setPickupPoint(points.get(i++));
                }
            }

            //Проверка на максимально возможный объем товара для одного пункта выдачи
            if (!isEqualOrLessThanMaxDeliveryCapacity(orders, params)) {
                throw new RuntimeException("One of order's volume is more than max courier capacity");
            }

            // Разделить маршрут между курьерами с учетом ограничений
            List<List<DoublePoint>> courierRoutes = splitRouteForCouriers(startPoint, orders, params);

            double totalExpenses = calculateTotalExpenses(courierRoutes, params);
            double totalTime = calculateTotalTime(courierRoutes, params);

            OptimizationResult result = new OptimizationResult(courierRoutes, totalExpenses, totalTime);

            if (result.getTotalTime() <= params.getMAX_TIME()) {
                JSONObject resultJson = createResultGeoJson(result, params, requestDataJson);

                // Подготовка заголовков
                Map<String, Object> headers = new HashMap<>();
                headers.put("type", "static");

                // Отправка JSON в RabbitMQ с заголовками
                rabbitMQSender.send(RabbitConfig.QUEUE_NAME, resultJson.toString(), headers);
                return resultJson;
            } else {
                throw new RuntimeException("No valid optimization result found.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private List<DoublePoint> extractPointsFromGeoJson(JSONObject geoJson) throws JSONException {
        JSONArray features = geoJson.getJSONArray("features");
        List<DoublePoint> points = new ArrayList<>();

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            double x = coordinates.getDouble(0);
            double y = coordinates.getDouble(1);
            points.add(new DoublePoint(new double[]{x, y}));
        }

        return points;
    }

    private boolean isEqualOrLessThanMaxDeliveryCapacity(List<Order> orders, Parameters params) {
        for (Order order : orders) {
            if (order.getVolume() > params.getMAX_DELIVERY_CAPACITY()) {
                return false;
            }
        }
        return true;
    }

    private List<List<DoublePoint>> splitRouteForCouriers(DoublePoint startPoint,
                                                          List<Order> orders, Parameters params) {
        int maxCourierCount = params.getMAX_COUNT_COURIERS();
        double maxCourierCapacity = params.getMAX_COURIER_CAR_CAPACITY();

        // Sort orders by volume in descending order
        orders.sort((o1, o2) -> Double.compare(o2.getVolume(), o1.getVolume()));

        //Логирование orders
        for (Order order: orders) {
            System.out.println("Order " + order.getId());
            System.out.println("Volume " + order.getVolume());
            System.out.println("PickupPoint " + order.getPickupPoint());
        }

        PriorityQueue<Courier> pq = initializeCouriers(params.getMAX_COUNT_COURIERS());

        // List of courier routes
        List<List<DoublePoint>> courierRoutes = new ArrayList<>();

        for (int i = 0; i < maxCourierCount; i++) {
            courierRoutes.add(new ArrayList<>());
        }

        // Assign orders to couriers
        for (Order order : orders) {
            // Логирование очереди курьеров
            System.out.println();
            System.out.println("Очередь курьеров для заказа " + order.getId());
            pq.forEach(System.out::println);
            assignOrdersToCouriers(maxCourierCapacity, pq, courierRoutes, order);
            // Логирование очереди курьеров
            System.out.println();
            System.out.println("Очередь курьеров после заказа " + order.getId());
            pq.forEach(System.out::println);
        }

        // Solve TSP for each courier route
        for (List<DoublePoint> route : courierRoutes) {
            if (!route.isEmpty()) {
                route.add(0, startPoint); // Add start point at the beginning
                route.add(startPoint); // Add start point at the end
                List<DoublePoint> optimizedRoute = solveTSP(route);
                route.clear();
                route.addAll(optimizedRoute);
            }
        }

        return courierRoutes;
    }

    private void assignOrdersToCouriers(double maxCourierCapacity, PriorityQueue<Courier> pq,
                                        List<List<DoublePoint>> courierRoutes, Order order) {
        Courier courier = pq.poll();
        if (courier.getCurrentDistance() + order.getVolume() <= maxCourierCapacity) {
            List<DoublePoint> points = courierRoutes.get(courier.getId());
            DoublePoint point = order.getPickupPoint();
            points.add(point);
            courier.setCurrentDistance(courier.getCurrentDistance() + order.getVolume());
            courier.getVisitedPoints().add(point);
            courier.setCountPoints(courier.getVisitedPoints().size());
        }
        pq.add(courier);
    }

    private PriorityQueue<Courier> initializeCouriers(int count) {
        PriorityQueue<Courier> pq = new PriorityQueue<>(Comparator.comparingDouble(Courier::getCurrentDistance));
        for (int i = 0; i < count; i++) {
            pq.add(new Courier(i, 0, new ArrayList<>()));
        }
        return pq;
    }

    private List<DoublePoint> solveTSP(List<DoublePoint> points) {
        // Implement a simple greedy TSP algorithm for demonstration purposes
        List<DoublePoint> tspRoute = new ArrayList<>();
        tspRoute.add(points.remove(0)); // Start point
        while (!points.isEmpty()) {
            DoublePoint lastPoint = tspRoute.get(tspRoute.size() - 1);
            DoublePoint nearestPoint = findNearestPoint(lastPoint, points);
            tspRoute.add(nearestPoint);
            points.remove(nearestPoint);
        }
        tspRoute.add(tspRoute.get(0)); // End at start point
        return tspRoute;
    }

    private DoublePoint findNearestPoint(DoublePoint fromPoint, List<DoublePoint> points) {
        DoublePoint nearestPoint = null;
        double nearestDistance = Double.MAX_VALUE;
        EuclideanDistance distance = new EuclideanDistance();
        for (DoublePoint point : points) {
            double currentDistance = distance.compute(fromPoint.getPoint(), point.getPoint());
            if (currentDistance < nearestDistance) {
                nearestDistance = currentDistance;
                nearestPoint = point;
            }
        }
        return nearestPoint;
    }

    private JSONObject createResultGeoJson(OptimizationResult result, Parameters params, JSONObject requestDataJson) throws JSONException {
        int i = 1;
        String[] colors = {"#FF5733", "#33FF57", "#5733FF", "#33FFFF", "#FF33FF"};
        JSONArray optimizedFeatures = new JSONArray();

        for (List<DoublePoint> courierRoute : result.getCourierRoutes()) {
            JSONArray courierLineCoordinates = new JSONArray();
            for (DoublePoint point : courierRoute) {
                courierLineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
            }
            String color = colors[(i++) % colors.length]; // Используем цвет из массива в соответствии с индексом
            JSONObject courierLineFeature = createLineFeature(courierLineCoordinates, "courier_route", color);
            optimizedFeatures.put(courierLineFeature);
        }

        JSONObject optimizedGeoJson = new JSONObject();
        optimizedGeoJson.put("type", "FeatureCollection");
        optimizedGeoJson.put("features", optimizedFeatures);

        JSONObject parametersJson = new JSONObject();
        parametersJson.put("total_expenses", result.getTotalExpenses());
        parametersJson.put("total_time", Time.convert(result.getTotalTime()).toJSON());
        parametersJson.put("couriers_count", params.getMAX_COUNT_COURIERS());

        JSONObject resultJson = new JSONObject();
        resultJson.put("static_model", optimizedGeoJson);
        resultJson.put("static_model_parameters", parametersJson);
        resultJson.put("request_data", requestDataJson);

        return resultJson;
    }

    private JSONObject createLineFeature(JSONArray coordinates, String type, String color) throws JSONException {
        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        JSONObject geometry = new JSONObject();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);
        feature.put("geometry", geometry);
        JSONObject properties = new JSONObject();
        properties.put("type", type);
        properties.put("color", color);
        feature.put("properties", properties);
        return feature;
    }

    private double calculateTotalExpenses(List<List<DoublePoint>> courierRoutes, Parameters params) {

        double totalCourierDistance = courierRoutes.stream()
                .mapToDouble(this::getTotalCourierRouteDistance)
                .sum();

        double totalExpenses = params.getFUEL_RATE_COURIER_CAR() * params.getFUEL_COST() * totalCourierDistance
                + params.getDRIVER_SALARY() * params.getMAX_COUNT_COURIERS() + params.getCOURIER_CAR_RATE();

        return Math.round(totalExpenses * 100.0) / 100.0;
    }

    private double calculateTotalTime(List<List<DoublePoint>> courierRoutes, Parameters params) {
        double maxCourierTime = 0;
        for (List<DoublePoint> courierRoute : courierRoutes) {
            double travelTime = getTotalCourierRouteDistance(courierRoute) / params.getMOB_STORAGE_SPEED();
            double processingTime = courierRoute.size() * params.getORDER_PROCESSING_TIME();
            maxCourierTime = Math.max(maxCourierTime, travelTime + processingTime);
        }

        return maxCourierTime;
    }

    private double getTotalCourierRouteDistance(List<DoublePoint> route) {
        double totalDistance = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            totalDistance += calculateDistance(route.get(i), route.get(i + 1));
        }
        return totalDistance;
    }

    private double calculateDistance(DoublePoint point1, DoublePoint point2) {
        return new EuclideanDistance().compute(point1.getPoint(), point2.getPoint());
    }
}
