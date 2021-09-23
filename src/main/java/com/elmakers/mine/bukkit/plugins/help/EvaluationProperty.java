package com.elmakers.mine.bukkit.plugins.help;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;

public class EvaluationProperty {
    private final String property;
    private final Class<?> propertyClass;
    private final List<Double> searchValues;
    private double defaultValue;

    private EvaluationProperty(String property, Class<?> propertyClass, double defaultValue, List<Double> searchValues) {
        this.property = property;
        this.propertyClass = propertyClass;
        this.defaultValue = defaultValue;
        this.searchValues = searchValues;
    }

    public static void register(Map<String, EvaluationProperty> map, String property, Class<?> propertyClass, double defaultValue, double[][] searchSpaces) {
        List<Double> values = new ArrayList<>();
        for (double[] searchSpace : searchSpaces) {
            int i = 0;
            double value = searchSpace[0] + searchSpace[2] * i;
            while (value <= searchSpace[1]) {
                values.add(value);
                i++;
                value = searchSpace[0] + searchSpace[2] * i;
            }
        }
        if (!values.contains(defaultValue)) {
            values.add(defaultValue);
        }
        Collections.sort(values);

        EvaluationProperty newProperty = new EvaluationProperty(property, propertyClass, defaultValue, values);
        map.put(property, newProperty);
    }

    public void set(double newValue) throws NoSuchFieldException, IllegalAccessException {
        Field valueField = propertyClass.getField(property);
        valueField.set(null, newValue);
    }

    public double get() throws NoSuchFieldException, IllegalAccessException {
        Field valueField = propertyClass.getField(property);
        return (double)valueField.get(null);
    }

    public void restoreDefaultValue() throws NoSuchFieldException, IllegalAccessException {
        set(defaultValue);
    }

    public void setDefaultValue(double newValue) throws NoSuchFieldException, IllegalAccessException {
        this.defaultValue = newValue;
        set(newValue);
    }

    public String getDescription() {
        return property;
    }

    public String getProperty() {
        return property;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    public List<Double> getSearchValues() {
        return searchValues;
    }
}
