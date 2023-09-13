package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Message;

public class SPelMessageWrapper {

    private SpeLHeader headers;
    private AbstractBody body;
    private String version;
    private String errorMessage;

    public SPelMessageWrapper(Message message) {
        if (message == null) {
            return;
        }

        this.headers = new SpeLHeader(message.getHeader());
        this.body = message.getBody();
        this.version = message.getVersion();
        this.errorMessage = message.getErrorMessage();
    }

    public SpeLHeader getHeaders() {
        return headers;
    }

    public AbstractBody getBody() {
        return body;
    }

    public String getVersion() {
        return version;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
