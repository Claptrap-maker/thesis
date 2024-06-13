package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// Параметры модели
@Data
@AllArgsConstructor
public class Parameters {
    // Расход топлива мобильного склада в литрах на км
    private double FUEL_RATE;
    // Стоимость топлива за литр
    private double FUEL_COST;
    // Стоимость обслуживания мобильного склада в день
    private double MOB_STORAGE_RATE;
    // Зарплата водителя мобильного склада в день
    private double DRIVER_SALARY;
    // Количество курьеров
    private int MAX_COUNT_COURIERS;
    // Зарплата курьера в день
    private double COURIER_SALARY;
    // Стоимость обслуживания техники курьера на точку
    private double COURIER_RATE;
    // Расход энергии в кВ (за час)
    private double ENERGY_CONSUMPTION;
    // Стоимость расхода энергии в час за 1 кВ
    private double ENERGY_CONSUMPTION_COST;
    // Максимальная вместимость мобильного склада (кг)
    private double MAX_MOB_STORAGE_CAPACITY;
    // Максимальная вместимость курьера (кг)
    private double MAX_COURIER_CAPACITY;
    // Максимальное время доставки (часы)
    private double MAX_TIME;
    // Время на обработку заказа (часы)
    private double ORDER_PROCESSING_TIME;
    // Скорость курьера (км/ч)
    private double COURIER_SPEED;
    // Скорость мобильного склада (км/ч)
    private double MOB_STORAGE_SPEED;
}
