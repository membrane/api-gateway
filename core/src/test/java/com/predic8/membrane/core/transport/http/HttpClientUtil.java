package com.predic8.membrane.core.transport.http;

public class HttpClientUtil {

    public static ConnectionManager getConnectionManager(HttpClient hc) {
        return hc.getConnectionManager();
    }
}
