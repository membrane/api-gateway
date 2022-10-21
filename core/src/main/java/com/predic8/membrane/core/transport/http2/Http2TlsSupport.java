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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

public class Http2TlsSupport {

    private static Method setApplicationProtocols;
    private static Method getApplicationProtocol;

    static {
        Method setApplicationProtocols = null;
        Method getApplicationProtocol = null;
        try {
            setApplicationProtocols = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            getApplicationProtocol = SSLSocket.class.getMethod("getApplicationProtocol");
        } catch (NoSuchMethodException e) {
            // old JDK, ignore
        }
        Http2TlsSupport.setApplicationProtocols = setApplicationProtocols;
        Http2TlsSupport.getApplicationProtocol = getApplicationProtocol;
    }

    public static void offerHttp2(SSLServerSocket sslss) {
        if (setApplicationProtocols == null)
            throw new RuntimeException("Support for HTTP/2 is only available when using newer JDKs.");

        SSLParameters sslp = sslss.getSSLParameters();
        try {
            setApplicationProtocols.invoke(sslp, new Object[] { new String[]{"h2", "http/1.1"} });
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        sslss.setSSLParameters(sslp);
    }

    public static void offerHttp2(SSLSocket ssls) {
        if (setApplicationProtocols == null)
            throw new RuntimeException("Support for HTTP/2 is only available when using newer JDKs.");

        SSLParameters sslp = ssls.getSSLParameters();
        try {
            setApplicationProtocols.invoke(sslp, new Object[] { new String[]{"h2", "http/1.1"} });
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        ssls.setSSLParameters(sslp);
    }

    /**
     * whether the usage of HTTP/2 was negotiated on this socket. only returns a valid response after the first byte has been read (=the TLS handshake completed).
     */
    public static boolean isHttp2(Socket s) {
        if (!(s instanceof SSLSocket))
            return false;
        if (getApplicationProtocol == null)
            return false; // old JDK
        try {
            String protocol = (String) getApplicationProtocol.invoke(s);
            return "h2".equals(protocol);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
