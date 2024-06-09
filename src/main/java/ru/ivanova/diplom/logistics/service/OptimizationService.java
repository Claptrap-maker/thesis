package ru.ivanova.diplom.logistics.service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.ivanova.diplom.logistics.model.Courier;
import ru.ivanova.diplom.logistics.model.Parameters;

import java.util.*;

@Service
public class OptimizationService {

    public JSONObject optimizeRoute(JSONObject geoJson, Parameters params) {
        try {
            JSONArray features = geoJson.getJSONArray("features");
            List<DoublePoint> points = new ArrayList<>();
            DoublePoint startPoint = null;

            // Извлечение координат пунктов выдачи
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject geometry = feature.getJSONObject("geometry");
                JSONArray coordinates = geometry.getJSONArray("coordinates");

                double x = coordinates.getDouble(0);
                double y = coordinates.getDouble(1);

                DoublePoint point = new DoublePoint(new double[]{x, y});
                points.add(point);

                // Определение начальной точки как первой точки
                if (startPoint == null) {
                    startPoint = new DoublePoint(new double[]{x, y});
                }
            }

            //Удаляем начальную точку (т.к. это точка склада, а не пункта выдачи)
            points.remove(0);

//            // Логирование точек
//            System.out.println("Points:");
//            for (DoublePoint point : points) {
//                System.out.println("Point: " + point);
//            }

            // Кластерный анализ
            int minClusters = 2;
            KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(Math.max(minClusters, points.size() / 2), 1000, new EuclideanDistance());
            List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

//            // Логирование кластеров
//            System.out.println("Number of clusters: " + clusters.size());
//            for (Cluster<DoublePoint> cluster : clusters) {
//                System.out.println("Cluster size: " + cluster.getPoints().size());
//                for (DoublePoint point : cluster.getPoints()) {
//                    System.out.println("Point: " + point);
//                }
//            }

//            //Логирование startpoint
//            System.out.println("StartPoint: " + startPoint);

            // Вычисление центров кластеров
            List<DoublePoint> clusterCenters = new ArrayList<>();
            for (CentroidCluster<DoublePoint> cluster : clusters) {
                double[] centerPoint = cluster.getCenter().getPoint();
                clusterCenters.add(new DoublePoint(centerPoint));
            }

//            // Логирование центров кластеров
//            System.out.println("Centres of clusters: " + clusterCenters.size());
//            clusterCenters.forEach(System.out::println);

            // Вычисление маршрута для мобильного склада через центры кластеров
            List<DoublePoint> optimizedRoute = calculateRoute(startPoint, clusterCenters);

//            // Логирование маршрута
//            System.out.println("Route: " + optimizedRoute.size());
//            optimizedRoute.forEach(System.out::println);

            JSONArray optimizedFeatures = new JSONArray();

            // Добавление маршрута мобильного склада
            JSONArray lineCoordinates = new JSONArray();

            for (DoublePoint point : optimizedRoute) {
                lineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
            }

            // Замыкание маршрута
            lineCoordinates.put(new JSONArray().put(startPoint.getPoint()[0]).put(startPoint.getPoint()[1]));

            // Создание линии для маршрута мобильного склада
            JSONObject lineFeature = createLineFeature(lineCoordinates, "route", "red");
            optimizedFeatures.put(lineFeature);

            // Приоритетная очередь для отслеживания текущей нагрузки каждого курьера
            PriorityQueue<Courier> pq = new PriorityQueue<>(Comparator.comparingDouble(Courier::getCurrentDistance));

            // Инициализация очереди курьерами
            for (int i = 0; i < params.getCOUNT_COURIERS(); i++) {
                pq.add(new Courier(i, 0, new ArrayList<>()));
            }

            for (CentroidCluster<DoublePoint> cluster : clusters) {
                List<DoublePoint> clusterPoints = cluster.getPoints();
                double[] clusterCenter = cluster.getCenter().getPoint();

                List<List<DoublePoint>> courierRoutes = calculateCourierRoutes(new DoublePoint(clusterCenter),
                        clusterPoints, params.getCOUNT_COURIERS(), pq);

                if (courierRoutes.isEmpty())
                    continue;

                // Логирование маршрута курьера
                System.out.println("==================================================");
                System.out.println("clusterCenter: " + new DoublePoint(clusterCenter));
                System.out.println("courierRoutes: " + courierRoutes.size());
                courierRoutes.forEach(route -> route.forEach(System.out::println));
                System.out.println("==================================================");

                for (List<DoublePoint> courierRoute : courierRoutes) {
                    JSONArray courierLineCoordinates = new JSONArray();
                    for (DoublePoint point : courierRoute) {
                        courierLineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
                    }
                    JSONObject courierLineFeature = createLineFeature(courierLineCoordinates, "courier_route", "#000000");
                    optimizedFeatures.put(courierLineFeature);
                }
            }

            // Вычисление общей суммы расходов и времени
            double totalExpenses = calculateTotalExpenses(optimizedRoute, pq, params);
            double totalTime = calculateTotalTime(optimizedRoute, pq, params);


            // Логирование суммарных расходов
            System.out.println("+++++++++++++++++++++");
            System.out.println("Общая сумма раходов: " + totalExpenses);
            System.out.println("+++++++++++++++++++++");

            // Логирование суммарного времени
            System.out.println("+++++++++++++++++++++");
            System.out.println("Общее время: " + totalTime);
            System.out.println("+++++++++++++++++++++");

            JSONObject optimizedGeoJson = new JSONObject();
            optimizedGeoJson.put("type", "FeatureCollection");
            optimizedGeoJson.put("features", optimizedFeatures);

            return optimizedGeoJson;

        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
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

    private double calculateTotalExpenses(List<DoublePoint> optimizedRoute, PriorityQueue<Courier> pq,
                                          Parameters params) {
        double totalMobStorageDistance = getTotalMobStorageDistance(optimizedRoute);

        double mobStorageExpenses = params.getFUEL_RATE() * params.getFUEL_COST() * totalMobStorageDistance
                + params.getDRIVER_SALARY() + params.getMOB_STORAGE_RATE();

        double courierExpenses = 0;
        for (Courier courier : pq) {
            courierExpenses += params.getCOURIER_SALARY() +
                    courier.getCountPoints() * params.getCOURIER_RATE() *
                            (courier.getCurrentDistance() / params.getCOURIER_SPEED());
        }

        return mobStorageExpenses + courierExpenses;
    }

    private double calculateTotalTime(List<DoublePoint> optimizedRoute, PriorityQueue<Courier> pq, Parameters params) {
        double totalMobStorageDistance = getTotalMobStorageDistance(optimizedRoute);

        double mobStorageTime = totalMobStorageDistance / params.getMOB_STORAGE_SPEED();

        double courierTime = 0;
        for (Courier courier : pq) {
            double travelTime = courier.getCurrentDistance() / params.getCOURIER_SPEED();
            double processingTime = courier.getCountPoints() * params.getORDER_PROCESSING_TIME();
            courierTime = Math.max(courierTime, travelTime + processingTime);
        }

        return mobStorageTime + courierTime;
    }

    private double getTotalMobStorageDistance(List<DoublePoint> optimizedRoute) {
        double totalMobStorageDistance = 0;
        for (int i = 1; i < optimizedRoute.size(); i++) {
            totalMobStorageDistance += calculateDistance(optimizedRoute.get(i - 1), optimizedRoute.get(i));
        }
        return totalMobStorageDistance;
    }

    // Метод для расчета расстояния между двумя точками
    private double calculateDistance(DoublePoint point1, DoublePoint point2) {
        double x1 = point1.getPoint()[0];
        double y1 = point1.getPoint()[1];
        double x2 = point2.getPoint()[0];
        double y2 = point2.getPoint()[1];
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    // Метод для расчета оптимального маршрута (алгоритм ближайшего соседа)
    private List<DoublePoint> calculateRoute(DoublePoint startPoint, List<DoublePoint> points) {
        List<DoublePoint> route = new ArrayList<>();
        Set<DoublePoint> visited = new HashSet<>();
        DoublePoint currentPoint = startPoint;

        route.add(currentPoint);
        visited.add(currentPoint);

        while (visited.size() < points.size() + 1) {
            DoublePoint nearestPoint = null;
            double minDistance = Double.MAX_VALUE;

            for (DoublePoint point : points) {
                if (!visited.contains(point)) {
                    double distance = calculateDistance(currentPoint, point);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestPoint = point;
                    }
                }
            }

            if (nearestPoint != null) {
                route.add(nearestPoint);
                visited.add(nearestPoint);
                currentPoint = nearestPoint;
            }
        }

        return route;
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

            // Логгирование курьеров
            System.out.println("===========================================");
            System.out.println("Cluster Center: " + clusterCenter);
            System.out.println("Couriers: " + pq.size());
            pq.forEach(System.out::println);
            System.out.println("===========================================");

            courierRoutes.removeIf(List::isEmpty);
        }

        return courierRoutes;
    }

    private void addingPointAndDistance(DoublePoint point, Courier courier, List<DoublePoint> points) {
//        // Логгирование добавления точки
//        System.out.println("===========================================");
//        System.out.println("Points size: " + points.size());
//        System.out.println("Last point in points: " + points.get(points.size()-1));
//        System.out.println("Point I'm going to add: " + point);
//        System.out.println("===========================================");
        points.add(point);
        double distanceToAdd = calculateDistance(points.get(points.size() - 2), point);
        double newDistance = courier.getCurrentDistance() + distanceToAdd;
        courier.setCurrentDistance(newDistance);
    }
}