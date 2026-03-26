package com.budget;

import javafx.beans.property.*;

public class Expense {
    private final IntegerProperty id;
    private final StringProperty title;
    private final DoubleProperty amount;

    public Expense(int id, String title, double amount) {
        this.id = new SimpleIntegerProperty(id);
        this.title = new SimpleStringProperty(title);
        this.amount = new SimpleDoubleProperty(amount);
    }

    public IntegerProperty idProperty() { return id; }
    public StringProperty titleProperty() { return title; }
    public DoubleProperty amountProperty() { return amount; }
}