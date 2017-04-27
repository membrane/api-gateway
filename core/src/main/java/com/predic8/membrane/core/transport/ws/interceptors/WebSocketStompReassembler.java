/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import com.predic8.membrane.core.util.EndOfStreamException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

        // TODO: this changes, as soon as we introduce the STOMPInterceptor
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

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

    private void modifyOriginalFrameWithExchange(WebSocketFrame wsStompFrame, Exchange exc) throws IOException {
        baos.reset();
        if (exc.getRequest().getMethod().isEmpty()) {
            // this is a heart-beat
            baos.write('\n');
        } else {
            exc.getRequest().writeSTOMP(baos);
            /*
            builder.append(exc.getRequest().getMethod()).append("\n");
            for (HeaderField header : exc.getRequest().getHeader().getAllHeaderFields()) {
                if (!header.equals(Header.CONTENT_LENGTH))
                    builder.append(header.getHeaderName()).append(":").append(header.getValue()).append("\n");
            }
            builder.append("\n");
            builder.append(exc.getRequest().getBody());
            */

            baos.write(0);
        }

        //byte[] payload = builder.toString().getBytes();

        wsStompFrame.setPayload(baos.toByteArray());
    }

    private Exchange convertToExchange(WebSocketFrame wsStompFrame) throws IOException, EndOfStreamException {
        byte[] realPayload = new byte[(int) wsStompFrame.getPayloadLength()];
        System.arraycopy(wsStompFrame.getPayload(), 0, realPayload, 0, (int) wsStompFrame.getPayloadLength());

        if (wsStompFrame.getPayloadLength() == 0)
            throw new IOException("Empty STOMP frame.");

        ByteArrayInputStream bais = new ByteArrayInputStream(wsStompFrame.getPayload(), 0, (int) wsStompFrame.getPayloadLength() - 1);
        Request request = new Request();

        if (isHeartBeat(wsStompFrame)) {
            request.setMethod("");
            request.setBody(new Body(bais));
        } else {
            if (wsStompFrame.getPayload()[(int) wsStompFrame.getPayloadLength() - 1] != 0)
                throw new IOException("STOMP frame is not terminated by \\0.");

            request.read(bais, true);
        }

        /*
        String payload = new String(realPayload);
        String verb = payload.substring(0, payload.indexOf('\n'));
        payload = payload.replace(verb + "\n", "");

        int contentLength = -1;
        Header headersObj = new Header();
        if(payload.indexOf("\n\n") > -1) {
            // this is a non-heart-beat

            String headers = payload.substring(0, payload.indexOf("\n\n"));

            String[] headersSplit = headers.split("\n");
            for (String header : headersSplit) {
                String[] headerKeyValue = header.split(":");
                if (headerKeyValue.length == 1)
                    headersObj.add(headerKeyValue[0], "");
                else
                    headersObj.add(headerKeyValue[0], header.substring(header.indexOf(":") + 1));
            }

            payload = payload.replace(headers + "\n\n", "");


            if (headersObj.hasContentLength())
                contentLength = headersObj.getContentLength();
        }
        Exchange result = new Request.Builder().method(verb).header(headersObj).body(payload).buildExchange();
        if (contentLength == -1)
            result.getRequest().getHeader().removeFields(Header.CONTENT_LENGTH);
        else
            result.getRequest().getHeader().setContentLength(contentLength);
        */

        Exchange result = new Exchange(null);
        result.setRequest(request);

        if (wsStompFrame.getOriginalExchange() != null)
            result.setProperty(Exchange.WS_ORIGINAL_EXCHANGE, wsStompFrame.getOriginalExchange());

        return result;
    }

    private boolean isHeartBeat(WebSocketFrame frame) {
        return
                (frame.getPayloadLength() == 2 && frame.getPayload()[0] == 0x0D && frame.getPayload()[1] == 0x0A) ||
                        (frame.getPayloadLength() == 1 && frame.getPayload()[0] == 0x0A);
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @MCChildElement
    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
