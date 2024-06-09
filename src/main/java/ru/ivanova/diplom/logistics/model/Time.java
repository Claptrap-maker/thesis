package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.json.JSONObject;

@Data
@AllArgsConstructor
public class Time {
    private int hours;
    private int minutes;
    private int seconds;

    // Метод для представления объекта в формате JSON
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("hours", hours);
        json.put("minutes", minutes);
        json.put("seconds", seconds);
        return json;
    }

    // Функция для конвертации double времени в объект Time
    public static Time convert(double timeInHours) {
        int hours = (int) timeInHours; // Получаем часы из double
        double remainingMinutes = (timeInHours - hours) * 60; // Получаем оставшиеся минуты
        int minutes = (int) remainingMinutes; // Получаем минуты из оставшихся минут (приведение типа к int отсекает дробную часть)
        double remainingSeconds = (remainingMinutes - minutes) * 60; // Получаем оставшиеся секунды
        int seconds = (int) Math.round(remainingSeconds); // Получаем секунды из оставшихся секунд, округляя до ближайшего целого

        return new Time(hours, minutes, seconds); // Возвращаем новый объект Time
    }
}
