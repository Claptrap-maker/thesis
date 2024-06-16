package ru.ivanova.diplom.logistics.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ru.ivanova.diplom.logistics.model.Order;

import java.util.ArrayList;
import java.util.List;

public class JsonUtils {
    public static List<Order> convertJsonArrayToOrderList(JSONArray ordersArray) throws JSONException {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ordersArray.length(); i++) {
            JSONObject orderObject = ordersArray.getJSONObject(i);
            int id = orderObject.getInt("id");
            double volume = orderObject.getDouble("volume");
            orders.add(new Order(id, volume));
        }

        return orders;
    }
}
