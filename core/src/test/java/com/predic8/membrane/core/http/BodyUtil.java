package com.predic8.membrane.core.http;

import com.predic8.membrane.core.transport.http.Connection;

import java.io.IOException;

public class BodyUtil {
    public static void closeConnection(AbstractBody b) throws IOException {
        boolean found = false;
        for (MessageObserver messageObserver : b.getObservers()) {
            if (messageObserver instanceof Connection) {
                found = true;
                ((Connection)messageObserver).close();
            }
        }
        if (!found)
            throw new RuntimeException("Could not close connection: no connection found.");
    }
}
