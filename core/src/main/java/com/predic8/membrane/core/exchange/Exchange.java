/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toMap;

public class Exchange extends AbstractExchange {

    /* Exchange Properties */
    public static final String HTTP_SERVLET_REQUEST = "membrane.servlet.request";
    public static final String ALLOW_WEBSOCKET = "membrane.use.websocket";
    public static final String ALLOW_TCP = "membrane.use.tcp";
    /**
     * Please note that this is a relic from RFC7540 and has been removed in RFC9113. It is present for backward
     * compatibility (i.e. for Java's internal HTTP client).
     */
    public static final String ALLOW_H2 = "membrane.use.h2";
    public static final String TRACK_NODE_STATUS = "membrane.track.node.status";
    public static final String SSL_CONTEXT = "membrane.ssl.context";
    public static final String OAUTH2 = "membrane.oauth2";
    public static final String SNI_SERVER_NAME = "membrane.sni.server.name";
    public static final String WS_ORIGINAL_EXCHANGE = "membrane.ws.original.exchange";
    public static final String SECURITY_SCHEMES = "membrane.security.schemes";

    private static final Logger log = LoggerFactory.getLogger(Exchange.class.getName());

    private AbstractHttpHandler handler;

    private String originalHostHeader = "";

    private Connection targetConnection;

    private int[] nodeStatusCodes;

    private Exception[] nodeExceptions;

    private long id;

    public Exchange(AbstractHttpHandler handler) {
        this.handler = handler;
        this.id = hashCode();
    }

    /**
     * For HttpResendRunnable
     *
     * @param original Exchange
     */
    public Exchange(Exchange original, AbstractHttpHandler handler) {
        super(original);
        this.handler = handler;
        originalHostHeader = original.originalHostHeader;
        id = hashCode();
    }

    public AbstractHttpHandler getHandler() {
        return handler;
    }

    public String getOriginalHostHeaderHost() {
        return originalHostHeader.replaceFirst(":.*", "");
    }

    public void block(Message msg) throws TerminateException {
        try {
            log.debug("Message thread waits");
            msg.wait();
            log.debug("Message thread received notify");
            if (isForcedToStop())
                throw new TerminateException("Force the exchange to stop.");
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
        }
    }

    public String getOriginalHostHeaderPort() {
        int pos = originalHostHeader.indexOf(':');
        if (pos == -1) {
            return "";
        } else {
            return originalHostHeader.substring(pos + 1);
        }
    }

    public String getOriginalHostHeader() {
        return originalHostHeader;
    }

    public void setOriginalHostHeader(String hostHeader) {
        originalHostHeader = hostHeader;
    }

    @Override
    public void setRequest(Request req) {
        super.setRequest(req);
        if (req != null)
            setOriginalHostHeader(req.getHeader().getHost());
    }

    public Connection getTargetConnection() {
        return targetConnection;
    }

    public void setTargetConnection(Connection con) {
        targetConnection = con;
    }

    public void collectStatistics() {
        proxy.getStatisticCollector().collect(this);
    }

    /**
     * Returns the relative original URI.
     * <p>
     * "original" meaning "as received by Membrane's transport".
     * <p>
     * To be used, for example, when generating self-referring web pages.
     */
    public String getRequestURI() {
        if (HttpUtil.isAbsoluteURI(getOriginalRequestUri())) {
            try {
                return new URL(getOriginalRequestUri()).getFile();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Request has a malformed URI: "
                                           + getOriginalRequestUri(), e);
            }
        }
        return getOriginalRequestUri();
    }

    public Map<String, String> getStringProperties() {
        return properties.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof String)
                .collect(toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    }

    public void setNodeStatusCode(int tryCounter, int code) {
        if (nodeStatusCodes == null) {
            nodeStatusCodes = new int[getDestinations().size()];
        }
        nodeStatusCodes[tryCounter % getDestinations().size()] = code;
    }

    public void trackNodeException(int tryCounter, Exception e) {
        if (!TRUE.equals(properties.get(TRACK_NODE_STATUS)))
            return;
        if (nodeExceptions == null) {
            nodeExceptions = new Exception[getDestinations().size()];
        }
        nodeExceptions[tryCounter % getDestinations().size()] = e;
    }

    @Override
    public void detach() {
        super.detach();
        handler = null;
    }

    public boolean canKeepConnectionAlive() {
        return getRequest().isKeepAlive() && getResponse().isKeepAlive();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Exchange createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
        Exchange exc = updateCopy(this, new Exchange(null), bodyUpdatedCallback, strategy, limit);
        exc.setId(this.getId());
        return exc;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int[] getNodeStatusCodes() {
        return nodeStatusCodes;
    }

    public Exception[] getNodeExceptions() {
        return nodeExceptions;
    }

    public String getInboundProtocol() {
        Proxy rule = getProxy();
        if (!(rule instanceof SSLableProxy sp))
            return "http";
        if (sp.getSslInboundContext() == null)
            return "http";
        else
            return "https";
    }
}
