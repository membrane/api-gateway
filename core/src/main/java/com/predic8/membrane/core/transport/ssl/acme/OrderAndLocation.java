package com.predic8.membrane.core.transport.ssl.acme;

public class OrderAndLocation {

    private Order order;
    private String location;

    public OrderAndLocation() {
    }

    public OrderAndLocation(Order order, String location) {
        this.order = order;
        this.location = location;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
