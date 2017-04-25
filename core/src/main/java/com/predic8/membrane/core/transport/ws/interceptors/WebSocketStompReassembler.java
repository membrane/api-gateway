package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "wsStompReassembler")
public class WebSocketStompReassembler implements WebSocketInterceptorInterface {

    List<Interceptor> interceptors = new ArrayList<>();

    @Override
    public void init(Router router) throws Exception {
        for (Interceptor i : interceptors)
            i.init(router);
    }

    @Override
    public void handleFrame(WebSocketFrame wsStompFrame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        if (wsStompFrame.getOpcode() != 1) {
            sender.handleFrame(wsStompFrame);
            return;
        }
        Exchange exc = convertToExchange(wsStompFrame);

        //if (frameTravelsToRight) {
        if (true) {
            for (int i = 0; i < interceptors.size(); i++)
                if (interceptors.get(i).handleRequest(exc) != Outcome.CONTINUE)
                    return;

        } else {
            for (int i = interceptors.size(); i >= 0; i--)
                //if (interceptors.get(i).handleResponse(exc) != Outcome.CONTINUE)
                //    return;
                break;
        }

        modifyOriginalFrameWithExchange(wsStompFrame, exc);
        sender.handleFrame(wsStompFrame);
    }

    private StringBuilder builder = new StringBuilder();

    private void modifyOriginalFrameWithExchange(WebSocketFrame wsStompFrame, Exchange exc) {
        builder.setLength(0);
        builder.append(exc.getRequest().getMethod()).append("\n");
        for (HeaderField header : exc.getRequest().getHeader().getAllHeaderFields()) {
            if (!header.equals(Header.CONTENT_LENGTH))
                builder.append(header.getHeaderName()).append(":").append(header.getValue()).append("\n");
        }
        builder.append("\n");
        builder.append(exc.getRequest().getBody());

        byte[] payload = builder.toString().getBytes();

        wsStompFrame.setPayload(payload);
    }

    private Exchange convertToExchange(WebSocketFrame wsStompFrame) {
        byte[] realPayload = new byte[(int) wsStompFrame.getPayloadLength()];
        System.arraycopy(wsStompFrame.getPayload(), 0, realPayload, 0, (int) wsStompFrame.getPayloadLength());
        String payload = new String(realPayload);
        String verb = payload.substring(0, payload.indexOf('\n'));
        payload = payload.replace(verb + "\n", "");

        String headers = payload.substring(0, payload.indexOf("\n\n"));
        Header headersObj = new Header();
        String[] headersSplit = headers.split("\n");
        for (String header : headersSplit) {
            String[] headerKeyValue = header.split(":");
            if (headerKeyValue.length == 1)
                headersObj.add(headerKeyValue[0], "");
            else
                headersObj.add(headerKeyValue[0], headerKeyValue[1]);
        }

        payload = payload.replace(headers + "\n\n", "");

        boolean hasContentLength = headersObj.hasContentLength();
        Exchange result = new Request.Builder().method(verb).header(headersObj).body(payload).buildExchange();
        if (!hasContentLength && result.getRequest().getHeader().hasContentLength())
            result.getRequest().getHeader().removeFields(Header.CONTENT_LENGTH);
        return result;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
