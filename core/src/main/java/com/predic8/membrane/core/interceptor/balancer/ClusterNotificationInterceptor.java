/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.http.Response.noContent;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.parseQueryString;

/**
 * @description Receives control messages to dynamically modify the configuration of a {@link LoadBalancingInterceptor}.
 * @explanation See also examples/loadbalancer-client-2 in the Membrane API Gateway distribution.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "clusterNotification")
public class ClusterNotificationInterceptor extends AbstractInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ClusterNotificationInterceptor.class.getName());

    private final Pattern urlPattern = Pattern.compile("/clustermanager/(up|down|takeout)/?\\??(.*)");

    private int timeout = 0;

    public ClusterNotificationInterceptor() {
        name = "cluster notification";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        log.debug(exc.getOriginalRequestUri());

        Matcher m = urlPattern.matcher(exc.getOriginalRequestUri());

        if (!m.matches()) return CONTINUE;

        log.debug("request received: {}", m.group(1));

        Map<String, String> params;
        try {
            params = getParams(exc);
            if (params.get("host") == null || params.get("port") == null)
                throw new IllegalArgumentException("Missing host/port");

            if (isTimedout(params)) {
                exc.setResponse(Response.forbidden().build());
                return ABORT;
            }
        } catch (RuntimeException e) {
            exc.setResponse(Response.badRequest().build());
            return ABORT;
        }

        updateClusterManager(m, params);

        exc.setResponse(noContent().build());
        return RETURN;
    }

    private void updateClusterManager(Matcher m, Map<String, String> params) {
        if ("up".equals(m.group(1))) {
            up(
                    router,
                    getBalancerParam(params),
                    getClusterParam(params),
                    params.get("host"),
                    getPortParam(params));
        } else if ("down".equals(m.group(1))) {
            down(
                    router,
                    getBalancerParam(params),
                    getClusterParam(params),
                    params.get("host"),
                    getPortParam(params));
        } else {
            takeout(
                    router,
                    getBalancerParam(params),
                    getClusterParam(params),
                    params.get("host"),
                    getPortParam(params));
        }
    }

    private boolean isTimedout(Map<String, String> params) {
        if (timeout <= 0) return false;
        String time = params.get("time");
        if (time == null)
            throw new IllegalArgumentException("Missing time");
        long ts = Long.parseLong(time);
        return System.currentTimeMillis() - ts > timeout;
    }

    private int getPortParam(Map<String, String> params) {
        return Integer.parseInt(params.get("port"));
    }

    private String getClusterParam(Map<String, String> params) {
        return params.get("cluster") == null ? Cluster.DEFAULT_NAME : params.get("cluster");
    }

    private String getBalancerParam(Map<String, String> params) {
        return params.get("balancer") == null ? Balancer.DEFAULT_NAME : params.get("balancer");
    }

    private Map<String, String> getParams(Exchange exc) {

        String uri = exc.getOriginalRequestUri();
        int qStart = uri.indexOf('?');
        if (qStart == -1 || qStart + 1 == uri.length())
            return new HashMap<>();
        return parseQueryString(exc.getOriginalRequestUri().substring(
                qStart + 1), ERROR);
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * @description Timestamp invalidation period. (0=unlimited)
     * @example 5000
     */
    @MCAttribute
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public String getShortDescription() {
        return "Sets the status of load-balancer nodes to UP or DOWN, based on the request attributes.";
    }

}
