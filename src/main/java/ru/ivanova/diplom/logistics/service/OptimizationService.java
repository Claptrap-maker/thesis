package ru.ivanova.diplom.logistics.service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OptimizationService {

    public JSONObject optimizeRoute(JSONObject geoJson, int couriers) {
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

            // Логирование точек
            System.out.println("Points:");
            for (DoublePoint point : points) {
                System.out.println("Point: " + point);
            }

            // Кластерный анализ
            int minClusters = 2;
            KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(Math.max(minClusters, points.size() / 2), 1000, new EuclideanDistance());
            List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

            // Логирование кластеров
            System.out.println("Number of clusters: " + clusters.size());
            for (Cluster<DoublePoint> cluster : clusters) {
                System.out.println("Cluster size: " + cluster.getPoints().size());
                for (DoublePoint point : cluster.getPoints()) {
                    System.out.println("Point: " + point);
                }
            }

            //Логирование startpoint
            System.out.println("StartPoint: " + startPoint);

            // Вычисление центров кластеров
            List<DoublePoint> clusterCenters = new ArrayList<>();
            for (CentroidCluster<DoublePoint> cluster : clusters) {
                double[] centerPoint = cluster.getCenter().getPoint();
                clusterCenters.add(new DoublePoint(centerPoint));
            }

            // Логирование центров кластеров
            System.out.println("Centres of clusters: " + clusterCenters.size());
            clusterCenters.forEach(System.out::println);

            // Вычисление маршрута для мобильного склада через центры кластеров
            List<DoublePoint> optimizedRoute = calculateRoute(startPoint, clusterCenters);

            // Логирование маршрута
            System.out.println("Route: " + optimizedRoute.size());
            optimizedRoute.forEach(System.out::println);

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

            for (CentroidCluster<DoublePoint> cluster : clusters) {
                List<DoublePoint> clusterPoints = cluster.getPoints();
                double[] clusterCenter = cluster.getCenter().getPoint();

                List<List<DoublePoint>> courierRoutes = calculateCourierRoutes(new DoublePoint(clusterCenter),
                        clusterPoints, couriers);

                if (courierRoutes.isEmpty())
                    continue;

                // Логирование маршрута курьера
                System.out.println("courierRoutes: " + courierRoutes.size());
                courierRoutes.forEach(route -> route.forEach(System.out::println));

                for (List<DoublePoint> courierRoute : courierRoutes) {
                    JSONArray courierLineCoordinates = new JSONArray();
                    for (DoublePoint point : courierRoute) {
                        courierLineCoordinates.put(new JSONArray().put(point.getPoint()[0]).put(point.getPoint()[1]));
                    }
                    JSONObject courierLineFeature = createLineFeature(courierLineCoordinates, "courier_route", "#000000");
                    optimizedFeatures.put(courierLineFeature);
                }
            }

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
                                                           int couriers) {
        List<List<DoublePoint>> courierRoutes = new ArrayList<>();

        if (clusterPoints.size() != 1) {

            for (int i = 0; i < couriers; i++) {
                courierRoutes.add(new ArrayList<>());
                courierRoutes.get(i).add(clusterCenter); // Начало маршрута с центра кластера
            }

            // Приоритетная очередь для отслеживания текущей нагрузки каждого курьера
            PriorityQueue<Courier> pq = new PriorityQueue<>(Comparator.comparingDouble(c -> c.currentDistance));

            // Инициализация очереди курьерами
            for (int i = 0; i < couriers; i++) {
                pq.add(new Courier(i, 0));
            }

            // Распределение точек по курьерам
            for (DoublePoint point : clusterPoints) {
                Courier courier = pq.poll();
                List<DoublePoint> points = courierRoutes.get(courier.id);
                if (points.size() % 2 == 0) {
                    points.add(clusterCenter);
                }
                points.add(point);
                double distanceToAdd = calculateDistance(points
                        .get(points.size() - 2), point);
                courier.currentDistance += distanceToAdd;
                pq.add(courier);
            }

            // Завершение маршрута каждого курьера центром кластера
            for (int i = 0; i < couriers; i++) {
                if (courierRoutes.get(i).size() % 2 == 0) {
                    courierRoutes.get(i).add(clusterCenter); // Завершение маршрута центром кластера
                }
            }
        }

        return courierRoutes;
    }

    // Класс для хранения информации о курьере
    private static class Courier {
        int id;
        double currentDistance;

        public Courier(int id, double currentDistance) {
            this.id = id;
            this.currentDistance = currentDistance;
        }
    }
}