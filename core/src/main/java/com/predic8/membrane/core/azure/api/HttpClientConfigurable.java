package com.predic8.membrane.core.azure.api;

import com.predic8.membrane.core.transport.http.HttpClient;

public interface HttpClientConfigurable<T> {
    HttpClient http();
    T config();
}
