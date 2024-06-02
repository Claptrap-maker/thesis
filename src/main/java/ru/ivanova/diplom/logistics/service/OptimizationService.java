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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OptimizationService {

    public JSONObject optimizeRoute(JSONObject geoJson) {
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
            KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(5, 1000, new EuclideanDistance());
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
            JSONObject lineFeature = createLineFeature(lineCoordinates, "route");
            optimizedFeatures.put(lineFeature);

            JSONObject optimizedGeoJson = new JSONObject();
            optimizedGeoJson.put("type", "FeatureCollection");
            optimizedGeoJson.put("features", optimizedFeatures);

            return optimizedGeoJson;

        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private JSONObject createLineFeature(JSONArray coordinates, String type) throws JSONException {
        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        JSONObject geometry = new JSONObject();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);
        feature.put("geometry", geometry);
        JSONObject properties = new JSONObject();
        properties.put("type", type);
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
}