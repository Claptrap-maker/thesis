package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// Параметры модели
@Data
@AllArgsConstructor
public class Parameters {
    // Расход топлива мобильного склада в литрах на км
    private double FUEL_RATE_MOB_STORAGE;
    // Расход топлива машины курьера в литрах на км
    private double FUEL_RATE_COURIER_CAR;
    // Стоимость топлива за литр
    private double FUEL_COST;
    // Стоимость обслуживания мобильного склада в день
    private double MOB_STORAGE_RATE;
    // Стоимость обслуживания машины курьера в день
    private double COURIER_CAR_RATE;
    // Зарплата водителя мобильного склада / курьера на машине в день
    private double DRIVER_SALARY;
    // Максимальное количество курьеров
    private int MAX_COUNT_COURIERS;
    // Зарплата курьера на самокате в день
    private double COURIER_SALARY;
    // Стоимость обслуживания самоката курьера
    private double COURIER_SCOOTER_RATE;
    // Расход энергии в кВ (за час)
    private double ENERGY_CONSUMPTION;
    // Стоимость расхода энергии в час за 1 кВ
    private double ENERGY_CONSUMPTION_COST;
    // Максимальная вместимость/объем машины курьера (в кубометрах)
    private double MAX_COURIER_CAR_CAPACITY;
    // Максимальная вместимость/объем заказа на 1 точку пункта выдачи (в кубометрах)
    private double MAX_DELIVERY_CAPACITY;
    // Максимальное время доставки (часы)
    private double MAX_TIME;
    // Среднее время на обработку заказа (часы)
    private double ORDER_PROCESSING_TIME;
    // Средняя скорость самоката курьера (км/ч)
    private double COURIER_SCOOTER_SPEED;
    // Средняя скорость мобильного склада / машины курьера (км/ч)
    private double MOB_STORAGE_SPEED;
}
