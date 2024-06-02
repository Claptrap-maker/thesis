package ru.ivanova.diplom.logistics.model;

import org.apache.commons.math3.ml.clustering.Clusterable;

public class PickupPoint implements Clusterable {
    private double[] points;

    public PickupPoint(double latitude, double longitude) {
        this.points = new double[]{latitude, longitude};
    }

    @Override
    public double[] getPoint() {
        return points;
    }

    public double getLatitude() {
        return points[0];
    }

    public double getLongitude() {
        return points[1];
    }
}
