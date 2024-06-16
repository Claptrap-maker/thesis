package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.util.List;

@Data
public class OptimizationResult {
    private List<DoublePoint> optimizedRoute;
    private List<List<DoublePoint>> courierRoutes;
    private double distanceMobStorage;
    private List<Double> distanceCouriers;
    private double totalExpenses;
    private double totalTime;
    private int optimalCouriersCount;

    public OptimizationResult(List<List<DoublePoint>> courierRoutes, double totalExpenses, double totalTime) {
        this.courierRoutes = courierRoutes;
        this.totalExpenses = totalExpenses;
        this.totalTime = totalTime;
    }

    public OptimizationResult(List<DoublePoint> optimizedRoute, List<List<DoublePoint>> courierRoutes, double totalExpenses, double totalTime, int optimalCouriersCount) {
        this.optimizedRoute = optimizedRoute;
        this.courierRoutes = courierRoutes;
        this.totalExpenses = totalExpenses;
        this.totalTime = totalTime;
        this.optimalCouriersCount = optimalCouriersCount;
    }
}
