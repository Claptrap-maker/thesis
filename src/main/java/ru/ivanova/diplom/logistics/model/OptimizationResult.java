package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.util.List;

@Data
@AllArgsConstructor
public class OptimizationResult {
    private List<DoublePoint> optimizedRoute;
    private List<List<DoublePoint>> courierRoutes;
    private double totalExpenses;
    private double totalTime;
    private int optimalCouriersCount;
}
