package com.example.dailyfocus.data;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    @TypeConverter
    public static String fromSubtaskList(List<Subtask> subtasks) {
        if (subtasks == null) {
            return null;
        }
        Gson gson = new Gson();
        return gson.toJson(subtasks);
    }

    @TypeConverter
    public static List<Subtask> toSubtaskList(String subtasksString) {
        if (subtasksString == null) {
            return new ArrayList<>();
        }
        Gson gson = new Gson();
        Type type = new TypeToken<List<Subtask>>() {}.getType();
        return gson.fromJson(subtasksString, type);
    }
}