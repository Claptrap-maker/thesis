package ru.ivanova.diplom.logistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class Time {
    private int hours;
    private int minutes;
    private int seconds;

    // Метод для представления объекта в формате JSON
    public JSONObject toJSON() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hours", hours);
        map.put("minutes", minutes);
        map.put("seconds", seconds);

        JSONObject json = new JSONObject(map);

        // Логгирование для отладки
        System.out.println("JSON объект времени: " + json.toString());

        return json;
    }

    // Функция для конвертации double времени в объект Time
    public static Time convert(double timeInHours) {
        int hours = (int) timeInHours; // Получаем часы из double
        double remainingMinutes = (timeInHours - hours) * 60; // Получаем оставшиеся минуты
        int minutes = (int) remainingMinutes; // Получаем минуты из оставшихся минут (приведение типа к int отсекает дробную часть)
        double remainingSeconds = (remainingMinutes - minutes) * 60; // Получаем оставшиеся секунды
        int seconds = (int) Math.round(remainingSeconds); // Получаем секунды из оставшихся секунд, округляя до ближайшего целого

        // Если количество секунд >= 60, добавляем одну минуту
        if (seconds >= 60) {
            seconds -= 60;
            minutes += 1;
        }

        // Если количество минут >= 60, добавляем один час
        if (minutes >= 60) {
            minutes -= 60;
            hours += 1;
        }

        // Логгирование для отладки
        System.out.println("Часы: " + hours + ", Минуты: " + minutes + ", Секунды: " + seconds);

        return new Time(hours, minutes, seconds); // Возвращаем новый объект Time
    }
}
