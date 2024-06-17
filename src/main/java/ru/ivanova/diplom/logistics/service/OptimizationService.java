package ru.ivanova.diplom.logistics.service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ivanova.diplom.logistics.config.RabbitConfig;
import ru.ivanova.diplom.logistics.model.Courier;
import ru.ivanova.diplom.logistics.model.OptimizationResult;
import ru.ivanova.diplom.logistics.model.Parameters;
import ru.ivanova.diplom.logistics.model.Time;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class OptimizationService {

    private static final double MAX_CLUSTER_RADIUS = 5.0;
    private static final int MAX_COUNT_CLUSTERS = 10;
    private final RabbitMQSender rabbitMQSender;

    @Autowired
    public OptimizationService(RabbitMQSender rabbitMQSender) {
        this.rabbitMQSender = rabbitMQSender;
    }

    public JSONObject optimizeRoute(JSONObject geoJson, Parameters params, JSONObject requestDataJson) {
        try {
            List<DoublePoint> points = extractPointsFromGeoJson(geoJson);
            DoublePoint startPoint = points.remove(0); // Начальная точка - первый элемент

            List<CompletableFuture<OptimizationResult>> futures = new ArrayList<>();

            for (int clusterCount = 2; clusterCount <= MAX_COUNT_CLUSTERS; clusterCount++) {
                for (int couriers = 3; couriers <= params.getMAX_COUNT_COURIERS(); couriers++) {
                    for (double clusterRadius = 0.5; clusterRadius <= MAX_CLUSTER_RADIUS; clusterRadius += 0.1) {
                        final int finalClusterCount = clusterCount;
                        final int finalCouriers = couriers;
                        final double finalClusterRadius = clusterRadius;

                        CompletableFuture<OptimizationResult> future = CompletableFuture.supplyAsync(() -> {
                            Parameters newParams = new Parameters(
                                    params.getFUEL_RATE_MOB_STORAGE(),
                                    params.getFUEL_RATE_COURIER_CAR(),
                                    params.getFUEL_COST(),
                                    params.getMOB_STORAGE_RATE(),
                                    params.getCOURIER_CAR_RATE(),
                                    params.getDRIVER_SALARY(),
                                    params.getMAX_COUNT_COURIERS(),
                                    params.getCOURIER_SALARY(),
                                    params.getCOURIER_SCOOTER_RATE(),
                                    params.getENERGY_CONSUMPTION(),
                                    params.getENERGY_CONSUMPTION_COST(),
                                    params.getMAX_COURIER_CAR_CAPACITY(),
                                    params.getMAX_DELIVERY_CAPACITY(),
                                    params.getMAX_TIME(),
                                    params.getORDER_PROCESSING_TIME(),
                                    params.getCOURIER_SCOOTER_SPEED(),
                                    params.getMOB_STORAGE_SPEED()
                            );
                            return calculateOptimization(points, startPoint, newParams, finalClusterCount,
                                    finalClusterRadius, finalCouriers);
                        });
                        futures.add(future);
                    }
                }
            }

            futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingDouble(OptimizationResult::getTotalExpenses))
                    .findFirst().stream().toList().forEach(result ->
                            System.out.println("Расходы: " + result.getTotalExpenses()
                            + "\nЗатраченное время: " + Time.convert(result.getTotalTime())
                            + "\nРасстояние, пройденное мобильным складом: " + result.getDistanceMobStorage()
                            + "\nРасстояния, пройденные курьерами: "
                                    + result.getDistanceCouriers().stream().map(String::valueOf)
                                    .collect(Collectors.joining(", "))));

            OptimizationResult bestResult = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(result -> result.getTotalTime() <= params.getMAX_TIME())
                    .min(Comparator.comparingDouble(OptimizationResult::getTotalExpenses))
                    .orElse(null);

            if (bestResult != null) {
                JSONObject resultJson = createResultGeoJson(bestResult, requestDataJson);

                // Подготовка заголовков
                Map<String, Object> headers = new HashMap<>();
                headers.put("type", "dynamic");

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

    private OptimizationResult calculateOptimization(List<DoublePoint> points, DoublePoint startPoint, Parameters params,
                                                     int clusterCount, double clusterRadius, int couriers) {
        JDKRandomGenerator randomGenerator = new JDKRandomGenerator();
        randomGenerator.setSeed(42);
        KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(clusterCount, 1000,
                new EuclideanDistance(), randomGenerator);
        List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);
        clusters = ensureMaxClusterRadius(clusters, clusterer, clusterRadius);

        List<DoublePoint> clusterCenters = clusters.stream()
                .map(cluster -> new DoublePoint(cluster.getCenter().getPoint()))
                .collect(Collectors.toList());

        List<DoublePoint> optimizedRoute = calculateRoute(startPoint, clusterCenters);
        PriorityQueue<Courier> pq = initializeCouriers(couriers);

        List<List<DoublePoint>> courierRoutes = calculateCourierRoutes(clusters, pq);

        double totalExpenses = calculateTotalExpenses(optimizedRoute, pq, params);
        double totalTime = calculateTotalTime(optimizedRoute, pq, params, clusters);

        OptimizationResult result =  new OptimizationResult(optimizedRoute, courierRoutes, totalExpenses, totalTime,
                couriers);

        result.setDistanceMobStorage(getTotalMobStorageDistance(optimizedRoute));
        List<Double> courierDistances = new ArrayList<>();
        for (Courier courier : pq) {
            courierDistances.add(courier.getCurrentDistance());
        }

        result.setDistanceCouriers(courierDistances);

        return result;
    }

    private List<List<DoublePoint>> calculateCourierRoutes(List<CentroidCluster<DoublePoint>> clusters,
                                                           PriorityQueue<Courier> pq) {
        List<List<DoublePoint>> courierRoutes = new ArrayList<>();
        for (CentroidCluster<DoublePoint> cluster : clusters) {
            List<DoublePoint> clusterPoints = cluster.getPoints();
            double[] clusterCenter = cluster.getCenter().getPoint();

            List<List<DoublePoint>> routes = calculateCourierRoutes(new DoublePoint(clusterCenter), clusterPoints,
                    pq.size(), pq);
            if (!routes.isEmpty()) {
                courierRoutes.addAll(routes);
            }
        }
        return courierRoutes;
    }

    private PriorityQueue<Courier> initializeCouriers(int count) {
        PriorityQueue<Courier> pq = new PriorityQueue<>(Comparator.comparingDouble(Courier::getCurrentDistance));
        for (int i = 0; i < count; i++) {
            pq.add(new Courier(i, 0, new ArrayList<>()));
        }
        return pq;
    }

    private List<List<DoublePoint>> calculateCourierRoutes(DoublePoint clusterCenter, List<DoublePoint> clusterPoints,
                                                             int couriers, PriorityQueue<Courier> pq) {
        List<List<DoublePoint>> courierRoutes = new ArrayList<>();

        if (clusterPoints.size() != 1) {

            for (int i = 0; i < couriers; i++) {
                courierRoutes.add(new ArrayList<>());
                courierRoutes.get(i).add(clusterCenter); // Начало маршрута с центра кластера
            }

            // Распределение точек по курьерам
            for (DoublePoint point : clusterPoints) {
                if (point.equals(clusterCenter))
                    continue;
                Courier courier = pq.poll();
                List<DoublePoint> points = courierRoutes.get(courier.getId());
//                // Логгирование добавление точки
//                System.out.println("===========================================");
//                System.out.println("Cluster Center: " + clusterCenter);
//                System.out.println("Point: " + point);
//                pq.forEach(System.out::println);
//                System.out.println("===========================================");
                if (!points.get(points.size()-1).equals(clusterCenter)) {
                    addingPointAndDistance(clusterCenter, courier, points);
                    addingPointAndDistance(clusterCenter, courier, points);
                } else {
                    courier.getVisitedPoints().add(point);
                    courier.setCountPoints(courier.getVisitedPoints().size());
                }
                addingPointAndDistance(point, courier, points);
                pq.add(courier);
            }

            for (int i = 0; i < couriers; i++) {
                List<DoublePoint> points = courierRoutes.get(i);
                if (!points.get(points.size() - 1).equals(clusterCenter)) {
                    for (Courier courier : pq) {
                        if (courier.getId() == i) {
                            addingPointAndDistance(clusterCenter, courier, points);
                            DoublePoint point = points.get(points.size() - 2);
                            List<DoublePoint> courierVisitedPoints = courier.getVisitedPoints();
                            if (!courierVisitedPoints.contains(point)) {
                                courierVisitedPoints.add(point);
                                courier.setCountPoints(courierVisitedPoints.size());
                            }
                            break;
                        }
                    }
                }
                else {
                    points.remove(points.size()-1);
                }
            }

//            // Логгирование курьеров
//            System.out.println("===========================================");
//            System.out.println("Cluster Center: " + clusterCenter);
//            System.out.println("Couriers: " + pq.size());
//            pq.forEach(System.out::println);
//            System.out.println("===========================================");

            courierRoutes.removeIf(List::isEmpty);
        }

        return courierRoutes;
    }

    private void addingPointAndDistance(DoublePoint point, Courier courier, List<DoublePoint> points) {
//        // Логгирование добавления точки
//        System.out.println();
//        System.out.println("Last point in points: " + points.get(points.size()-1));
//        System.out.println("Point I'm going to add: " + point);
//        System.out.println();
        points.add(point);
        double distanceToAdd = calculateDistance(points.get(points.size() - 2), point);
        double newDistance = courier.getCurrentDistance() + distanceToAdd;
        courier.setCurrentDistance(newDistance);

//        //Логирование дистанции курьера
//        System.out.println();
//        System.out.println("Курьер " + courier.getId() + " с новой дистанцией " + distanceToAdd);
//        System.out.println();
    }

    private JSONObject createResultGeoJson(OptimizationResult result, JSONObject requestDataJson) throws JSONException {
        JSONArray optimizedFeatures = new JSONArray();

        JSONArray lineCoordinates = new JSONArray();
        for (DoublePoint point : result.getOptimizedRoute()) {
            lineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
        }
        lineCoordinates.put(new JSONArray()
                .put(result.getOptimizedRoute().get(0).getPoint()[0])
                .put(result.getOptimizedRoute().get(0).getPoint()[1]));

        JSONObject lineFeature = createLineFeature(lineCoordinates, "route", "red");
        optimizedFeatures.put(lineFeature);

        for (List<DoublePoint> courierRoute : result.getCourierRoutes()) {
            JSONArray courierLineCoordinates = new JSONArray();
            for (DoublePoint point : courierRoute) {
                courierLineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
            }
            JSONObject courierLineFeature = createLineFeature(courierLineCoordinates, "courier_route", "#000000");
            optimizedFeatures.put(courierLineFeature);
        }

        JSONObject optimizedGeoJson = new JSONObject();
        optimizedGeoJson.put("type", "FeatureCollection");
        optimizedGeoJson.put("features", optimizedFeatures);

        JSONObject parametersJson = new JSONObject();
        parametersJson.put("total_expenses", result.getTotalExpenses());
        parametersJson.put("total_time", Time.convert(result.getTotalTime()).toJSON());
        parametersJson.put("optimal_couriers_count", result.getOptimalCouriersCount());

        JSONObject resultJson = new JSONObject();
        resultJson.put("dynamic_model", optimizedGeoJson);
        resultJson.put("dynamic_model_parameters", parametersJson);
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

    private double calculateTotalExpenses(List<DoublePoint> optimizedRoute, PriorityQueue<Courier> pq, Parameters params) {
        double totalMobStorageDistance = getTotalMobStorageDistance(optimizedRoute);

        double mobStorageExpenses = params.getFUEL_RATE_MOB_STORAGE() * params.getFUEL_COST() * totalMobStorageDistance
                + params.getDRIVER_SALARY() + params.getMOB_STORAGE_RATE();

        double courierExpenses = 0;
        for (Courier courier : pq) {
            double courierDeliveryTime = courier.getCurrentDistance() / params.getCOURIER_SCOOTER_SPEED();
            courierExpenses += params.getCOURIER_SALARY() +
                    courier.getCountPoints() * params.getCOURIER_SCOOTER_RATE() * courierDeliveryTime
                    + params.getENERGY_CONSUMPTION() * params.getENERGY_CONSUMPTION_COST() * courierDeliveryTime;
        }

        double totalExpenses = mobStorageExpenses + courierExpenses;

        return Math.round(totalExpenses * 100.0) / 100.0;
    }

    private double calculateTotalTime(List<DoublePoint> optimizedRoute, PriorityQueue<Courier> pq, Parameters params,
                                      List<CentroidCluster<DoublePoint>> clusters) {
        double totalMobStorageDistance = getTotalMobStorageDistance(optimizedRoute);

        double mobStorageTime = totalMobStorageDistance / params.getMOB_STORAGE_SPEED();

        double maxCourierTime = 0;
        for (Courier courier : pq) {
            double travelTime = courier.getCurrentDistance() / params.getCOURIER_SCOOTER_SPEED();
            double processingTime = courier.getCountPoints() * params.getORDER_PROCESSING_TIME();
            maxCourierTime = Math.max(maxCourierTime, travelTime + processingTime);
        }

        for (CentroidCluster<DoublePoint> cluster : clusters) {
            if (cluster.getPoints().size() == 1) {
                maxCourierTime += params.getORDER_PROCESSING_TIME();
            }
        }

        return mobStorageTime + maxCourierTime;
    }

    private double getTotalMobStorageDistance(List<DoublePoint> optimizedRoute) {
        double totalMobStorageDistance = 0;
        for (int i = 1; i < optimizedRoute.size(); i++) {
            totalMobStorageDistance += calculateDistance(optimizedRoute.get(i - 1), optimizedRoute.get(i));
        }
        return totalMobStorageDistance;
    }

    private double calculateDistance(DoublePoint point1, DoublePoint point2) {
        return Math.sqrt(Math.pow(point2.getPoint()[0] - point1.getPoint()[0], 2)
                + Math.pow(point2.getPoint()[1] - point1.getPoint()[1], 2));
    }

    private List<DoublePoint> calculateRoute(DoublePoint startPoint, List<DoublePoint> points) {
        List<DoublePoint> route = new ArrayList<>();
        route.add(startPoint);

        Set<DoublePoint> visited = new HashSet<>();
        visited.add(startPoint);

        while (visited.size() < points.size() + 1) {
            DoublePoint lastPoint = route.get(route.size() - 1);
            DoublePoint nextPoint = points.stream()
                    .filter(p -> !visited.contains(p))
                    .min(Comparator.comparingDouble(p -> calculateDistance(lastPoint, p)))
                    .orElse(null);

            if (nextPoint != null) {
                route.add(nextPoint);
                visited.add(nextPoint);
            }
        }

        route.add(startPoint);

        return route;
    }

    private List<CentroidCluster<DoublePoint>> ensureMaxClusterRadius(List<CentroidCluster<DoublePoint>> clusters,
                                                                      KMeansPlusPlusClusterer<DoublePoint> clusterer,
                                                                      double maxRadius) {
        List<CentroidCluster<DoublePoint>> newClusters = new ArrayList<>();

        for (CentroidCluster<DoublePoint> cluster : clusters) {
            List<DoublePoint> clusterPoints = cluster.getPoints();
            DoublePoint centroid = new DoublePoint(cluster.getCenter().getPoint());

            double maxDistance = clusterPoints.stream()
                    .mapToDouble(point -> calculateDistance(centroid, point))
                    .max()
                    .orElse(0);

            if (maxDistance > maxRadius) {
                newClusters.addAll(clusterer.cluster(clusterPoints));
            } else {
                newClusters.add(cluster);
            }
        }

        return newClusters;
    }
}