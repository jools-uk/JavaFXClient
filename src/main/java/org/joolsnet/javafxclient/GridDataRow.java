package org.joolsnet.javafxclient;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * JavaFX observable data model for a grid row.
 * Uses JavaFX properties to enable automatic UI updates when values change.
 */
public class GridDataRow {
    private final IntegerProperty id;
    private final StringProperty name;
    private final DoubleProperty price;
    private final IntegerProperty quantity;
    private final StringProperty status;
    private final StringProperty createdTimestamp;
    private final StringProperty modifiedTimestamp;
    private final DoubleProperty value;
    private final DoubleProperty bidPrice;
    private final IntegerProperty bidSize;
    private final DoubleProperty askPrice;
    private final IntegerProperty askSize;

    public GridDataRow(int id, String name, double price, int quantity, String status, 
                       String createdTimestamp, String modifiedTimestamp, double value,
                       double bidPrice, int bidSize, double askPrice, int askSize) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.price = new SimpleDoubleProperty(price);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.status = new SimpleStringProperty(status);
        this.createdTimestamp = new SimpleStringProperty(createdTimestamp);
        this.modifiedTimestamp = new SimpleStringProperty(modifiedTimestamp);
        this.value = new SimpleDoubleProperty(value);
        this.bidPrice = new SimpleDoubleProperty(bidPrice);
        this.bidSize = new SimpleIntegerProperty(bidSize);
        this.askPrice = new SimpleDoubleProperty(askPrice);
        this.askSize = new SimpleIntegerProperty(askSize);
    }

    // ID property
    public int getId() {
        return id.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public void setId(int id) {
        this.id.set(id);
    }

    // Name property
    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    // Price property
    public double getPrice() {
        return price.get();
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    // Quantity property
    public int getQuantity() {
        return quantity.get();
    }

    public IntegerProperty quantityProperty() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity.set(quantity);
    }

    // Status property
    public String getStatus() {
        return status.get();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    // Created Timestamp property
    public String getCreatedTimestamp() {
        return createdTimestamp.get();
    }

    public StringProperty createdTimestampProperty() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(String createdTimestamp) {
        this.createdTimestamp.set(createdTimestamp);
    }

    // Modified Timestamp property
    public String getModifiedTimestamp() {
        return modifiedTimestamp.get();
    }

    public StringProperty modifiedTimestampProperty() {
        return modifiedTimestamp;
    }

    public void setModifiedTimestamp(String modifiedTimestamp) {
        this.modifiedTimestamp.set(modifiedTimestamp);
    }

    // Value property
    public double getValue() {
        return value.get();
    }

    public DoubleProperty valueProperty() {
        return value;
    }

    public void setValue(double value) {
        this.value.set(value);
    }

    // Bid Price property
    public double getBidPrice() {
        return bidPrice.get();
    }

    public DoubleProperty bidPriceProperty() {
        return bidPrice;
    }

    public void setBidPrice(double bidPrice) {
        this.bidPrice.set(bidPrice);
    }

    // Bid Size property
    public int getBidSize() {
        return bidSize.get();
    }

    public IntegerProperty bidSizeProperty() {
        return bidSize;
    }

    public void setBidSize(int bidSize) {
        this.bidSize.set(bidSize);
    }

    // Ask Price property
    public double getAskPrice() {
        return askPrice.get();
    }

    public DoubleProperty askPriceProperty() {
        return askPrice;
    }

    public void setAskPrice(double askPrice) {
        this.askPrice.set(askPrice);
    }

    // Ask Size property
    public int getAskSize() {
        return askSize.get();
    }

    public IntegerProperty askSizeProperty() {
        return askSize;
    }

    public void setAskSize(int askSize) {
        this.askSize.set(askSize);
    }

    /**
     * Updates a field by name with a string value
     */
    public void updateField(String fieldName, String newValue) {
        switch (fieldName) {
            case "id":
                setId(Integer.parseInt(newValue));
                break;
            case "name":
                setName(newValue);
                break;
            case "price":
                setPrice(Double.parseDouble(newValue));
                break;
            case "quantity":
                setQuantity(Integer.parseInt(newValue));
                break;
            case "status":
                setStatus(newValue);
                break;
            case "createdTimestamp":
                setCreatedTimestamp(newValue);
                break;
            case "modifiedTimestamp":
                setModifiedTimestamp(newValue);
                break;
            case "value":
                setValue(Double.parseDouble(newValue));
                break;
            case "bidPrice":
                setBidPrice(Double.parseDouble(newValue));
                break;
            case "bidSize":
                setBidSize(Integer.parseInt(newValue));
                break;
            case "askPrice":
                setAskPrice(Double.parseDouble(newValue));
                break;
            case "askSize":
                setAskSize(Integer.parseInt(newValue));
                break;
        }
    }

    @Override
    public String toString() {
        return String.format("GridDataRow[id=%d, name=%s, price=%.2f, quantity=%d, status=%s, value=%.2f]",
                getId(), getName(), getPrice(), getQuantity(), getStatus(), getValue());
    }
}
