package ru.ivanova.diplom.logistics.model;

// Параметры модели
public class Parameters {
    // Расход топлива мобильного склада в литрах на км
    public static double FUEL_RATE = 0.1;
    // Стоимость топлива за литр
    public static double FUEL_COST = 50;
    // Стоимость обслуживания мобильного склада в день
    public static double MOB_STORAGE_RATE = 400;
    // Зарплата водителя мобильного склада в день
    public static double DRIVER_SALARY = 3800;
    // Зарплата курьера в день
    public static double COURIER_SALARY = 2500;
    // Стоимость обслуживания техники курьера на точку
    public static double COURIER_RATE = 10;
    // Максимальная вместимость мобильного склада (кг)
    public static double MAX_MOB_STORAGE_CAPACITY = 1000;
    // Максимальная вместимость курьера (кг)
    public static double MAX_COURIER_CAPACITY = 30;
    // Максимальное время доставки (часы)
    public static double MAX_TIME = 12;
    // Время на обработку заказа (часы)
    public static double ORDER_PROCESSING_TIME = 0.5;
    // Скорость курьера (км/ч)
    public static double COURIER_SPEED = 18;
    // Скорость мобильного склада (км/ч)
    public static double MOB_STORAGE_SPEED = 60;
    // Максимально допустимые расходы в день (рубли)
    public static double MAX_EXPENSES = 10000;
}
