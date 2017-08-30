package com.predic8.membrane.core.transport.http;

import java.io.IOException;

public class LineTooLongException extends IOException {
    public LineTooLongException(String line) {
        super(line);
    }
}
