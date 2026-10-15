/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http2.frame.RstStreamFrame;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.DNSCache;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.Util;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_NO_ERROR;

public class Http2ServerHandler extends AbstractHttpHandler {

    public static final byte[] PREFACE = new byte[]{0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2f, 0x32, 0x2e,
            0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d, 0x0d, 0x0a, 0x0d, 0x0a};
    private static final ExecutorService executor = Util.createNewThreadPool();
    public static final String HTTP2_SERVER = "h2_server";

    private final HttpServerHandler httpServerHandler;
    final Http2Logic logic;

    public Http2ServerHandler(HttpServerHandler httpServerHandler, Socket sourceSocket, InputStream srcIn, OutputStream srcOut, boolean showSSLExceptions) {
        super(httpServerHandler.getTransport());

        this.httpServerHandler = httpServerHandler;
        this.logic = new Http2Logic(executor, sourceSocket, srcIn, srcOut, showSSLExceptions, new Http2MessageHandler() {
            @Override
            public Message createMessage() {
                return new Request();
            }

            @Override
            public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
                Exchange exchange = new Exchange(httpServerHandler);
                exchange.setProperty(HTTP2_SERVER, true);
                exchange.setRequest((Request) message);

                exchange.received();
                DNSCache dnsCache = httpServerHandler.getTransport().getRouter().getDnsCache();
                InetAddress remoteAddr2 = sourceSocket.getInetAddress();
                String ip = dnsCache.getHostAddress(remoteAddr2);
                exchange.setRemoteAddrIp(ip);
                exchange.setRemoteAddr(httpServerHandler.getTransport().isReverseDNS() ? dnsCache.getHostName(remoteAddr2) : ip);

                exchange.setOriginalRequestUri(((Request)message).getUri());

                executor.submit(new Http2ExchangeHandler(streamInfo, httpServerHandler.getTransport(), logic.sender, logic.peerSettings, logic.peerFlowControl, exchange, showSSLExceptions, remoteAddr));
            }

            /** Answers 431 (RFC 6585) and, if the request is still open, ends it with RST_STREAM(NO_ERROR) (RFC 9113 §8.1). */
            @Override
            public void handleOversizedHeaders(StreamInfo streamInfo, boolean endStream) throws IOException {
                final var streamId = streamInfo.getStreamId();
                final var response = statusCode(431).build();
                logic.sender.send(streamId, (encoder, peerSettings) -> {
                    final var frames = new ArrayList<>(createHeadersFrames(response, response.getHeader(), streamId, encoder, peerSettings, true));
                    if (!endStream)
                        frames.add(RstStreamFrame.construct(streamId, ERROR_NO_ERROR));
                    return frames;
                });
            }
        });
    }

    public void handle() throws IOException, EndOfStreamException {
        byte[] preface = ByteUtil.readByteArray(logic.srcIn, 24);

        if (!isCorrectPreface(preface))
            throw new RuntimeException("Incorrect Preface.");

        logic.init();
        logic.handle();
    }

    private boolean isCorrectPreface(byte[] preface) {
        if (preface.length != PREFACE.length)
            return false;
        for (int i = 0; i < PREFACE.length; i++)
            if (preface[i] != PREFACE[i])
                return false;
        return true;
    }

    @Override
    public void shutdownInput() {
        throw new NotImplementedException("");
    }

    @Override
    public InetAddress getLocalAddress() {
        return httpServerHandler.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return httpServerHandler.getLocalPort();
    }

}