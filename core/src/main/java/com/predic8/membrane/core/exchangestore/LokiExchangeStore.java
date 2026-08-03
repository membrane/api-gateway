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

package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.google.common.cache.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.exchange.ExchangeState.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

/**
 * @description Sends exchanges to a <a href="https://grafana.com/oss/loki/">Grafana Loki</a> instance, where they can
 * be searched and visualized using LogQL and Grafana. Each exchange becomes one log line containing the exchange
 * (including headers and bodies) as JSON, so it can be unpacked in LogQL using the <code>| json</code> stage. Log lines
 * are labelled with <code>job</code> and with <code>api</code>, the name of the API the exchange passed through.
 * <p>Exchanges are batched and pushed to <code>/loki/api/v1/push</code> every <code>updateIntervalMs</code>
 * milliseconds. Only completed and failed exchanges are pushed: Loki cannot update a log line that has already been
 * written, so emitting one line per exchange means waiting until the exchange is done. Exchanges that never finish are
 * never pushed.</p>
 * <p>This store is write-only. The Membrane admin console cannot browse exchanges held in Loki; use Grafana for
 * that.</p>
 * @topic 4. Monitoring, Logging and Statistics
 * @yaml
 * <pre><code>
 * components:
 *   exchangeStore:
 *     lokiExchangeStore:
 *       url: http://localhost:3100
 *       job: gateway-eu
 * </code></pre>
 */
@MCElement(name = "lokiExchangeStore")
public class LokiExchangeStore extends AbstractPersistentExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(LokiExchangeStore.class);

    private static final String UNKNOWN_API = "unknown";

    private String url = "http://localhost:3100";
    private String job = "membrane";
    private String orgId;
    private HttpClientConfiguration httpClientConfig;

    private HttpClient client;
    private ObjectMapper mapper;

    /**
     * Exchanges seen but not yet pushed. Needed because {@link AbstractPersistentExchangeStore#snap(AbstractExchange,
     * com.predic8.membrane.core.interceptor.Interceptor.Flow)} looks up the request-phase snapshot to merge the response
     * into, and unlike other persistent stores we cannot read it back from Loki.
     */
    private final Cache<Long, AbstractExchangeSnapshot> pending = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES).build();

    @Override
    public void init(Router router) {
        client = router.getHttpClientFactory().createClient(httpClientConfig);
        mapper = new ObjectMapper();
        super.init(router);
    }

    @Override
    protected void addForStorage(AbstractExchangeSnapshot exc) {
        pending.put(exc.getId(), exc);
        super.addForStorage(exc);
    }

    @Override
    protected void writeToStore(List<AbstractExchangeSnapshot> exchanges) {
        List<AbstractExchangeSnapshot> finished = exchanges.stream().filter(LokiExchangeStore::isFinished).toList();
        if (finished.isEmpty())
            return;
        finished.forEach(exc -> pending.invalidate(exc.getId()));
        push(finished);
    }

    private void push(List<AbstractExchangeSnapshot> exchanges) {
        try {
            Request.Builder builder = Request.post(url + "/loki/api/v1/push").json(buildPushBody(exchanges));
            if (orgId != null)
                builder.header("X-Scope-OrgID", orgId);
            Exchange exc = builder.buildExchange();

            client.call(exc);

            Response response = exc.getResponse();
            if (response.getStatusCode() < 200 || response.getStatusCode() > 299)
                log.error("Loki rejected {} exchanges with status {}: {}", exchanges.size(),
                        response.getStatusCode(), response.getBodyAsStringDecoded());
        } catch (Exception e) {
            // Do not propagate: AbstractPersistentExchangeStore's update thread dies on an exception and would
            // stop storing exchanges for the rest of the router's lifetime.
            log.error("While pushing {} exchanges to Loki at {}.", exchanges.size(), url, e);
        }
    }

    private String buildPushBody(List<AbstractExchangeSnapshot> exchanges) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode streams = root.putArray("streams");
        for (var stream : exchanges.stream().collect(groupingBy(LokiExchangeStore::getApiName)).entrySet()) {
            ObjectNode streamNode = streams.addObject();
            streamNode.putObject("stream").put("job", job).put("api", stream.getKey());
            ArrayNode values = streamNode.putArray("values");
            for (AbstractExchangeSnapshot exc : stream.getValue().stream().sorted(comparingLong(LokiExchangeStore::getTimestampNs)).toList())
                values.addArray()
                        .add(String.valueOf(getTimestampNs(exc)))
                        .add(mapper.writeValueAsString(exc));
        }
        return mapper.writeValueAsString(root);
    }

    private static boolean isFinished(AbstractExchangeSnapshot exc) {
        return exc.getStatus() == COMPLETED || exc.getStatus() == FAILED;
    }

    private static String getApiName(AbstractExchangeSnapshot exc) {
        if (exc.getRule() == null || exc.getRule().getName() == null)
            return UNKNOWN_API;
        return exc.getRule().getName();
    }

    private static long getTimestampNs(AbstractExchangeSnapshot exc) {
        long millis = exc.getTime() != null ? exc.getTime().getTimeInMillis() : exc.getTimeReqReceived();
        return millis * 1_000_000;
    }

    @Override
    public AbstractExchangeSnapshot getFromStoreById(long id) {
        AbstractExchangeSnapshot snapshot = pending.getIfPresent(id);
        if (snapshot != null)
            return snapshot;
        // The exchange took longer than the 'pending' expiry. Returning an empty snapshot keeps snap() from failing in
        // the middle of the request flow, at the cost of a log line without the request.
        log.warn("No pending snapshot for exchange {}; it will be logged without its request.", id);
        return new AbstractExchangeSnapshot();
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        throw new UnsupportedOperationException("lokiExchangeStore is write-only. Query the exchanges in Grafana.");
    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        throw new UnsupportedOperationException("lokiExchangeStore is write-only. Query the exchanges in Grafana.");
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        throw new UnsupportedOperationException("lokiExchangeStore is write-only. Query the exchanges in Grafana.");
    }

    @Override
    public ExchangeQueryResult getFilteredSortedPaged(QueryParameter params, boolean useXForwardedForAsClientAddr) {
        throw new UnsupportedOperationException("lokiExchangeStore is write-only. Query the exchanges in Grafana.");
    }

    @Override
    public List<String> getUniqueValuesOf(String field) {
        throw new UnsupportedOperationException("lokiExchangeStore is write-only. Query the exchanges in Grafana.");
    }

    @Override
    public void collect(ExchangeCollector collector) {
        // nothing to collect: exchanges are not readable from Loki
    }

    @Override
    public void remove(AbstractExchange exchange) {
        throw new UnsupportedOperationException("lokiExchangeStore cannot remove exchanges. Configure retention in Loki.");
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {
        throw new UnsupportedOperationException("lokiExchangeStore cannot remove exchanges. Configure retention in Loki.");
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        throw new UnsupportedOperationException("lokiExchangeStore cannot remove exchanges. Configure retention in Loki.");
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description Base URL of the Loki instance. Exchanges are pushed to <tt>&lt;url&gt;/loki/api/v1/push</tt>.
     * @default http://localhost:3100
     * @example https://logs-prod-012.grafana.net
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getJob() {
        return job;
    }

    /**
     * @description Value of the <tt>job</tt> label attached to every log line. Use it to tell several Membrane
     * instances apart in Grafana.
     * @default membrane
     * @example gateway-eu
     */
    @MCAttribute
    public void setJob(String job) {
        this.job = job;
    }

    public String getOrgId() {
        return orgId;
    }

    /**
     * @description Tenant to write to, sent as the <tt>X-Scope-OrgID</tt> header. Required when Loki runs with
     * <tt>auth_enabled: true</tt>. Omit for a single-tenant Loki.
     * @example team-a
     */
    @MCAttribute
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    /**
     * @description Configuration of the HTTP client used to reach Loki. Use its <tt>&lt;authentication&gt;</tt> child
     * to supply the credentials of a hosted Loki such as Grafana Cloud.
     */
    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }
}
