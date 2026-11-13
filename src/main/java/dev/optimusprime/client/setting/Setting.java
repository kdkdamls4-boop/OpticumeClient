package dev.optimusprime.client.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibility = () -> true;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public void reset() {
        this.value = defaultValue;
    }

    public boolean isVisible() {
        return visibility == null || visibility.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return (S) this;
    }
}
