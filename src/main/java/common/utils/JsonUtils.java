package common.utils;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class JsonUtils {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonUtils() {
        // Utility class
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        try {
            return GSON.fromJson(json, typeOfT);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }
}
