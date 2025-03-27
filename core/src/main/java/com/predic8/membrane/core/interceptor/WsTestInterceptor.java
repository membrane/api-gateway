package com.predic8.membrane.core.interceptor;

import com.google.common.io.CountingOutputStream;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import org.jose4j.base64url.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static com.predic8.membrane.core.http.Header.CONNECTION;
import static com.predic8.membrane.core.http.Header.UPGRADE;
import static java.nio.charset.StandardCharsets.UTF_8;

@MCElement(name = "wsTest")
public class WsTestInterceptor extends AbstractInterceptor {
    Logger log = LoggerFactory.getLogger(WsTestInterceptor.class);

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (isRelevantForMe(exc))
            handle(exc);
        return Outcome.CONTINUE;
    }

    private void handle(Exchange exc) {
        try {
            Response res = new Response();
            res.setStatusCode(101);
            res.setStatusMessage("Switching Protocols");
            res.getHeader().add(CONNECTION, UPGRADE);
            res.getHeader().add(UPGRADE, "websocket");
            res.getHeader().add("Sec-WebSocket-Accept", computeKeyResponse(exc.getRequest().getHeader().getFirstValue("Sec-WebSocket-Key")));
            res.setBody(new EmptyBody());
            exc.setResponse(res);

            HttpServerHandler handler = (HttpServerHandler) exc.getHandler();
            handler.getSourceSocket().setSoTimeout(0);
            OutputStream out = handler.getSrcOut();

            res.write(out, false);

            Thread t = new Thread(() -> {
                try {
                    var frameAssembler = new WebSocketFrameAssembler(handler.getSrcIn(), exc);

                    frameAssembler.readFrames(frame -> {
                        try {
                            log.info("received {}", frame.toString());
                        } catch (Exception e) {
                            log.error("Error logging frame.", e);
                        }
                    });

                } catch (IOException e) {
                    log.error("Error while reading frames.", e);
                } finally {
                    try {
                        handler.getSourceSocket().close();
                    } catch (IOException e) {
                        log.error("Error while closing handler", e);
                    }

                }

            });

            t.start();

            Thread.sleep(5000);


            while(!Thread.currentThread().isInterrupted()) {
                byte[] mask = new byte[4];

                String msg = "Hallo vom Server! " + System.currentTimeMillis();

                WebSocketFrame wsf = new WebSocketFrame(true, false, false, false, 1, false, mask, msg.getBytes(UTF_8));
                log.info("sending {}", wsf);

                wsf.write(handler.getSrcOut());

                Thread.sleep(5000);
            }

            // do not return from this method

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("returned from a method i should not return");
        }
    }

    static String computeKeyResponse(String key) {
        String t = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest dig = null;
        try {
            dig = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = dig.digest(t.getBytes());
        return Base64.encode(hash);
    }


    private boolean isRelevantForMe(Exchange exc) {
        return exc.getRequest().getMethod().equals("GET") &&
                "websocket".equalsIgnoreCase(getUpgradeProtocol(exc.getRequest()));
    }

    private String getUpgradeProtocol(Request req) {
        if (req.getHeader().getSingleValues(CONNECTION).noneMatch(v -> v.equalsIgnoreCase(UPGRADE)))
            return null;
        return req.getHeader().getFirstValue(UPGRADE);
    }

}
