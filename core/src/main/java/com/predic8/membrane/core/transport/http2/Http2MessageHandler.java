package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.Message;

public interface Http2MessageHandler {

    Message createMessage();

    void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr);

}
