package com.predic8.membrane.core.transport.ssl.acme;

public class Identifier {
    public static final String TYPE_DNS = "dns";

    String type;
    String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
