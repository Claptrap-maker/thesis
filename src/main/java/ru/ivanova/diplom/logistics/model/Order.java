package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.math3.ml.clustering.DoublePoint;

@Data
public class Order {
    private int id;
    private double volume;
    private DoublePoint pickupPoint;

    public Order(int id, double volume) {
        this.id = id;
        this.volume = volume;
    }
}
