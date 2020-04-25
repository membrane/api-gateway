package com.predic8.membrane.core.transport.http2.frame;

import java.io.IOException;

public class FatalConnectionException extends IOException {
    public FatalConnectionException(int error) {
    }
}
