package com.predic8.membrane.core.transport.ssl.acme;

import org.joda.time.DateTime;

import java.util.Date;

public class AcmeErrorLog {
    String message;
    boolean fatal;
    DateTime time;

    public AcmeErrorLog(String message, boolean fatal, DateTime time) {
        this.message = message;
        this.fatal = fatal;
        this.time = time;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFatal() {
        return fatal;
    }

    public void setFatal(boolean fatal) {
        this.fatal = fatal;
    }

    public DateTime getTime() {
        return time;
    }

    public void setTime(DateTime time) {
        this.time = time;
    }
}
