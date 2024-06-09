package ru.ivanova.diplom.logistics.model;

import lombok.Data;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.util.List;

// Класс для хранения информации о курьере
@Data
public class Courier {
    private int id;
    private double currentDistance;
    private int countPoints;
    private List<DoublePoint> visitedPoints;

    public Courier(int id, double currentDistance, List<DoublePoint> visitedPoints) {
        this.id = id;
        this.currentDistance = currentDistance;
        this.visitedPoints = visitedPoints;
        this.countPoints = visitedPoints.size();
    }

    @Override
    public String toString() {
        return "Courier{" +
                "id=" + id +
                ", currentDistance=" + currentDistance +
                ", countPoints=" + countPoints +
                ", visitedPoints=" + visitedPoints.stream().map(doublePoint -> doublePoint.toString() + " ").reduce("", String::concat) +
                '}';
    }
}
