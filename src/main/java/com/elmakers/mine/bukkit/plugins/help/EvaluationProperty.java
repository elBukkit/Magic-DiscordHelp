package com.elmakers.mine.bukkit.plugins.help;

import java.lang.reflect.Field;
import java.util.Map;

import org.bukkit.ChatColor;

public class EvaluationProperty {
    private final String property;
    private final Class<?> propertyClass;
    private double defaultValue;

    private EvaluationProperty(String property, Class<?> propertyClass, double defaultValue) {
        this.property = property;
        this.propertyClass = propertyClass;
        this.defaultValue = defaultValue;
    }

    public static void register(Map<String, EvaluationProperty> map, String property, Class<?> propertyClass, double defaultValue) {
        EvaluationProperty newProperty = new EvaluationProperty(property, propertyClass, defaultValue);
        map.put(property, newProperty);
    }

    public void set(double newValue) throws NoSuchFieldException, IllegalAccessException {
        Field valueField = propertyClass.getField(property);
        valueField.set(null, newValue);
    }

    public void restoreDefaultValue() throws NoSuchFieldException, IllegalAccessException {
        set(defaultValue);
    }

    public void setDefaultValue(double newValue) throws NoSuchFieldException, IllegalAccessException {
        this.defaultValue = newValue;
        set(newValue);
    }

    public String getDescription() {
        return ChatColor.DARK_AQUA + propertyClass.getSimpleName() + ChatColor.GRAY + "." + ChatColor.AQUA + "  " + property;
    }

    public String getProperty() {
        return property;
    }
}
