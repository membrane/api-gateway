/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLProtocolException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SSLContextCollectionTest {

    private SSLContext context;
    private SSLProvider provider;

    @BeforeEach
    void setUp() {
        context = mock(SSLContext.class);
        when(context.constructHostNamePattern()).thenReturn("example.com");
        provider = new SSLContextCollection.Builder().add(context).useCollection().build();
    }

    @Test
    void sniHostNameSelectsMatchingContext() throws Exception {
        provider.wrapAcceptedSocket(socketReceiving(clientHelloWithSni("example.com")));
        verify(context).wrap(any(), any(), anyInt());
    }

    /**
     * RFC 6066 forbids a trailing dot in the SNI host name. It is a malformed ClientHello, not an
     * internal error.
     */
    @Test
    void sniHostNameWithTrailingDotIsProtocolError() throws Exception {
        final var socket = socketReceiving(clientHelloWithSni("example.com."));
        assertThrows(SSLProtocolException.class, () -> provider.wrapAcceptedSocket(socket));
        verify(context, never()).wrap(any(), any(), anyInt());
    }

    @Test
    void handshakeOtherThanClientHelloIsProtocolError() throws Exception {
        final var socket = socketReceiving(handshakeRecord((byte) 2 /* server_hello */, new byte[4]));
        assertThrows(SSLProtocolException.class, () -> provider.wrapAcceptedSocket(socket));
        verify(context, never()).wrap(any(), any(), anyInt());
    }

    private static Socket socketReceiving(byte[] data) {
        return new Socket() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(data);
            }

            @Override
            public OutputStream getOutputStream() {
                return new ByteArrayOutputStream();
            }

            @Override
            public void setSoTimeout(int timeout) {
            }
        };
    }

    /**
     * Minimal TLS 1.2 ClientHello record with a single server_name extension.
     */
    private static byte[] clientHelloWithSni(String hostName) {
        final var name = hostName.getBytes(US_ASCII);

        final var sni = ByteBuffer.allocate(9 + name.length)
                .putShort((short) 0x0000)              // extension type: server_name
                .putShort((short) (5 + name.length))   // extension length
                .putShort((short) (3 + name.length))   // server_name_list length
                .put((byte) 0)                         // name_type: host_name
                .putShort((short) name.length)
                .put(name)
                .array();

        final var body = ByteBuffer.allocate(2 + 32 + 1 + 4 + 2 + 2 + sni.length)
                .putShort((short) 0x0303)              // client_version: TLS 1.2
                .put(new byte[32])                     // random
                .put((byte) 0)                         // session_id length
                .putShort((short) 2).putShort((short) 0x002f) // cipher_suites
                .put((byte) 1).put((byte) 0)           // compression_methods: null
                .putShort((short) sni.length)          // extensions length
                .put(sni)
                .array();

        return handshakeRecord((byte) 1 /* client_hello */, body);
    }

    private static byte[] handshakeRecord(byte handshakeType, byte[] body) {
        final var handshakeLength = 4 + body.length;
        return ByteBuffer.allocate(5 + handshakeLength)
                .put((byte) 22)                        // content type: handshake
                .putShort((short) 0x0301)              // record version
                .putShort((short) handshakeLength)
                .put(handshakeType)
                .put((byte) 0).putShort((short) body.length) // uint24 length
                .put(body)
                .array();
    }
}
