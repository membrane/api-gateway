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

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.net.Socket;

public class Http2TlsSupport {

    public static void offerHttp2(SSLServerSocket sslss) {
        SSLParameters sslp = sslss.getSSLParameters();
        sslp.setApplicationProtocols(new String[]{"h2", "http/1.1"});
        sslss.setSSLParameters(sslp);
    }

    public static void offerHttp2(SSLSocket ssls) {
        SSLParameters sslp = ssls.getSSLParameters();
        sslp.setApplicationProtocols(new String[]{"h2", "http/1.1"});
        ssls.setSSLParameters(sslp);
    }

    /**
     * whether the usage of HTTP/2 was negotiated on this socket. only returns a valid response after the first byte has been read (=the TLS handshake completed).
     */
    public static boolean isHttp2(Socket s) {
        if (!(s instanceof SSLSocket ssls))
            return false;
        return "h2".equals(ssls.getApplicationProtocol());
    }
}
