/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.util.*;

import java.net.*;
import java.net.URI;

public record HostColonPort(boolean useSSL, String host, int port) {

    public HostColonPort(boolean useSSL, String hostAndPort) {
        this(useSSL, hostPart(hostAndPort), portPart(hostAndPort, useSSL ? 443 : 80));
    }

    public HostColonPort(String host, int port) {
        this(false, host, port);
    }

    public HostColonPort(URL url) throws MalformedURLException {
        this(url.getProtocol().endsWith("s"), url.getHost(), HttpUtil.getPort(url));
    }

    public String getProtocol() {
        // TODO shouldn't this check useSSL instead?
        return (isHttpsPort() ? "https" : "http");
    }

    public URL toURL() throws URISyntaxException, MalformedURLException {
        return new URI(getProtocol(), this.toString(), null).toURL();
//		return new URI("%s://%s".formatted(getProtocol(), this)).toURL();
    }

    public String getUrl() {
        return "%s://%s".formatted(getProtocol(), this);
    }

    public URI toURI() throws URISyntaxException {
        return new URI(this.getProtocol(),null, this.host, this.port, null, null,null);
    }

    public static HostColonPort fromURI(String uri) throws URISyntaxException, MalformedURLException {
        var url = new URI(uri).toURL();
        var isSSL = "https".equals(url.getProtocol());
        return new HostColonPort(isSSL, url.getHost(), HttpUtil.getPort(url));
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    private boolean isHttpsPort() {
        return port == 443 || port == 8443;
    }

    private static String hostPart(String addr) {
        var colon = addr.indexOf(":");
        return (colon > -1 ? addr.substring(0, colon) : addr);
    }

    private static int portPart(String addr, int defaultValue) {
        var colon = addr.indexOf(":");
        return (colon > -1 ? Integer.parseInt(addr.substring(colon + 1)) : defaultValue);
    }
}
