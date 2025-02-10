package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCTextContent;

public class CollectionName {
    public String name;

    public String getName() {
        return name;
    }

    @MCTextContent
    public void setName(String name) {
        this.name = name;
    }
}
