/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.prometheus;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.util.stream.Collectors.*;

/**
 * @description Exposes some of Membrane's internal metrics in the Prometheus format.
 *
 * See also examples/monitoring-tracing/prometheus for a demo, including a screenshot.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "prometheus")
public class PrometheusInterceptor extends AbstractInterceptor {

    static final Logger LOG = LoggerFactory.getLogger(PrometheusInterceptor.class);
    static volatile boolean issuedDuplicateRuleNameWarning = false;

    public PrometheusInterceptor() {
        name = "prometheus";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Context ctx = new Context();
        buildPrometheusStyleResponse(ctx);
        exc.setResponse(ok(ctx.sb.toString()).contentType( "text/plain; version=0.0.4").build());
        return RETURN;
    }

    private static class Context {
        final StringBuilder sb = new StringBuilder();

        final List<StringBuilder> dynamic = new ArrayList<>();

        final StringBuilder count = new StringBuilder();
        final StringBuilder good_count = new StringBuilder();
        final StringBuilder good_time = new StringBuilder();
        final StringBuilder good_bytes_req_body = new StringBuilder();
        final StringBuilder good_bytes_res_body = new StringBuilder();
        final StringBuilder duplicate_rule_name = new StringBuilder();
        final StringBuilder ssl_haskeyandcert = new StringBuilder();
        final StringBuilder ssl_validfrom_ms = new StringBuilder();
        final StringBuilder ssl_validuntil_ms = new StringBuilder();
        final StringBuilder openapi_validation = new StringBuilder();
        final StringBuilder lb_active_nodes = new StringBuilder();

        final HashSet<String> seenRules = new HashSet<>();

        private StringBuilder getNew() {
            StringBuilder b = new StringBuilder();
            dynamic.add(b);
            return b;
        }

        private void reset() {
            count.setLength(0);
            good_count.setLength(0);
            good_time.setLength(0);
            good_bytes_req_body.setLength(0);
            good_bytes_res_body.setLength(0);
            duplicate_rule_name.setLength(0);
            ssl_haskeyandcert.setLength(0);
            ssl_validfrom_ms.setLength(0);
            ssl_validuntil_ms.setLength(0);
            openapi_validation.setLength(0);
            lb_active_nodes.setLength(0);

            dynamic.forEach(s -> s.setLength(0));
        }

        private void resetAll() {
            sb.setLength(0);
            seenRules.clear();
        }

        private void collect() {
            sb.append(count);
            sb.append(good_count);
            sb.append(good_time);
            sb.append(good_bytes_req_body);
            sb.append(good_bytes_res_body);
            sb.append(duplicate_rule_name);
            sb.append(ssl_haskeyandcert);
            sb.append(ssl_validfrom_ms);
            sb.append(ssl_validuntil_ms);
            sb.append(openapi_validation);
            sb.append(lb_active_nodes);

            dynamic.forEach(sb::append);
        }
    }

    private void buildPrometheusStyleResponse(Context ctx) {
        ctx.resetAll();
        ctx.reset();
        for (Proxy r : router.getRuleManager().getRules()) {
            if (!ctx.seenRules.add(prometheusCompatibleName(r.getName()))) {
                // the prometheus format is not allowed to contain the same metric more than once
                if (issuedDuplicateRuleNameWarning)
                    continue;
                LOG.warn("The prometheus interceptor detected the same rule name more than once: {}",r.getName());
                issuedDuplicateRuleNameWarning = true;
                continue;
            }

            buildStatuscodeLines(ctx, r);
            buildBuckets(ctx, r);
            buildActive(ctx, r);

            if (r.isActive()) {
                if (r instanceof SSLableProxy sp) {
                    SSLContext sslib = sp.getSslInboundContext();
                    if (sslib != null)
                        buildSSLLines(ctx, r, sslib);
                }
            }

            if (r instanceof APIProxy) {
                buildOpenAPIValidatorLines(ctx, (APIProxy) r);
            }

        }
        buildLoadBalancerLines(ctx);
        buildDuplicateRuleNameWarning(ctx, issuedDuplicateRuleNameWarning);
        ctx.collect();

    }

    private void buildLoadBalancerLines(Context ctx) {
        ctx.lb_active_nodes.append("# TYPE membrane_lb_node_status gauge\n");
        for (Cluster cl : collectClusters(router)) {
            for (Node node : cl.getNodes()) {
                ctx.lb_active_nodes.append("membrane_lb_node_status");
                ctx.lb_active_nodes.append("{node=\"");
                ctx.lb_active_nodes.append(prometheusCompatibleName(node.toString()));
                ctx.lb_active_nodes.append("\",cluster=\"");
                ctx.lb_active_nodes.append(prometheusCompatibleName(cl.getName()));
                ctx.lb_active_nodes.append("\"} ");
                ctx.lb_active_nodes.append(node.getStatus() == UP ? 1 : 0);
                ctx.lb_active_nodes.append("\n");
            }
        }
    }

    private void buildOpenAPIValidatorLines(Context ctx, APIProxy proxy) {
        for (Map.Entry<ValidationStatsKey, Integer> e : proxy.getValidationStatisticCollector().getStats().entrySet()) {
            buildLine(ctx.openapi_validation, proxy.getName(), e.getValue(), e.getKey().getLabels(), "openapi_validation");
        }
    }

    private void buildLine(StringBuilder sb, String ruleName, long value, Map<String, String> labels, String postFix) {
        String prometheusName = prometheusCompatibleName("membrane_" + postFix);
        if (sb.isEmpty()) {
            sb.append("# TYPE ");
            sb.append(prometheusName);
            sb.append(" counter\n");
        }
        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",");

        for (Map.Entry<String, String> e : labels.entrySet()) {
            sb.append(e.getKey());
            sb.append("=\"");
            sb.append(e.getValue());  // TODO Sanitize / Ok aber \ nicht wie prometheusCompatibleName
            sb.append("\",");
        }
        sb.append("} ");
        sb.append(value);
        sb.append("\n");
    }

    private void buildSSLLines(Context ctx, Proxy r, SSLContext sslib) {
        boolean hasKeyAndCert = sslib.hasKeyAndCertificate();
        buildSSLLine(ctx.ssl_haskeyandcert, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_haskeyandcert", hasKeyAndCert ? 1 : 0);
        if (hasKeyAndCert) {
            buildSSLLine(ctx.ssl_validfrom_ms, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_validfrom_ms", sslib.getValidFrom());
            buildSSLLine(ctx.ssl_validuntil_ms, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_validuntil_ms", sslib.getValidUntil());
        }
    }

    private void buildSSLLine(StringBuilder sb, String ruleName, String prometheusContextTypeName, String metric, long value) {
        String prometheusName = prometheusCompatibleName("membrane_" + metric);

        if (sb.isEmpty()) {
            sb.append("# TYPE ");
            sb.append(prometheusName);
            sb.append(" gauge\n");
        }

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",type=\"");
        sb.append(prometheusCompatibleName(prometheusContextTypeName));
        sb.append("\"} ");
        sb.append(value);
        sb.append("\n");
    }

    private void buildDuplicateRuleNameWarning(Context ctx, boolean hasDuplicateRuleName) {
        ctx.duplicate_rule_name.append("# TYPE membrane_duplicate_rule_name gauge\n");

        buildLine(ctx.duplicate_rule_name, "duplicate_rule_name", hasDuplicateRuleName ? 1 : 0);
    }

    private void buildActive(Context ctx, Proxy r) {
        if (ctx.duplicate_rule_name.isEmpty())
            ctx.duplicate_rule_name.append("# TYPE membrane_rule_active gauge\n");

        buildBucketLine(ctx.duplicate_rule_name, r.getName(), "rule_active", r.isActive() ? 1 : 0);
    }

    private void buildBuckets(Context ctx, Proxy proxy) {
        proxy.getStatisticCollector().getTimeStatisticsByStatusCodeRange().forEach((code, tc) -> tc.getTrackedTimes().forEach((name, tt) -> {
            if (tt.isEmpty())
                return;

            StringBuilder sb = ctx.getNew();
            tt.forEach((le, count) -> {
                if (le.equals("SUM") || le.equals("COUNT"))
                    return;

                buildBucketLine(sb, proxy.getName(), code, le, count, name);
            });
            buildBucketLine(sb, proxy.getName(), code, name + "_sum", tt.get("SUM"));
            buildBucketLine(sb, proxy.getName(), code, name + "_count", tt.get("COUNT"));
        }));
    }

    private void buildStatuscodeLines(Context ctx, Proxy proxy) {
        proxy.getStatisticCollector().getStatisticsByStatusCodes().forEach((key, value) -> {
            buildLine(ctx.count, proxy.getName(), value.getCount(), "code", key, "count");
            buildLine(ctx.good_count, proxy.getName(), value.getGoodCount(), "code", key, "good_count");
            buildLine(ctx.good_time, proxy.getName(), value.getGoodTotalTime(), "code", key, "good_time");
            buildLine(ctx.good_bytes_req_body, proxy.getName(), value.getGoodTotalBytesSent(), "code", key, "good_bytes_req_body");
            buildLine(ctx.good_bytes_res_body, proxy.getName(), value.getGoodTotalBytesReceived(), "code", key, "good_bytes_res_body");
        });
    }

    private void buildLine(StringBuilder sb, String label, long value) {
        String prometheusName = prometheusCompatibleName("membrane_" + label);

        sb.append(prometheusName);
        sb.append(" ");
        sb.append(value);
        sb.append("\n");
    }

    private void buildBucketLine(StringBuilder sb, String ruleName, String label, long value) {
        String prometheusName = prometheusCompatibleName("membrane_" + label);

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\"} ");
        sb.append(value);
        sb.append("\n");
    }

    private void buildBucketLine(StringBuilder sb, String ruleName, int code, String label, long value) {
        String prometheusName = prometheusCompatibleName("membrane_" + label);

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",code=\"");
        sb.append(code);
        sb.append("\"} ");
        sb.append(value);
        sb.append("\n");
    }

    private void buildBucketLine(StringBuilder sb, String ruleName, int code, String le, long valueCount, String infix) {
        String prometheusName = prometheusCompatibleName("membrane_" + infix + "_bucket");
        if (sb.isEmpty()) {
            sb.append("# TYPE ");
            sb.append(prometheusName);
            sb.append(" histogram\n");
        }

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",code=\"");
        sb.append(code);
        sb.append("\",le=\"");
        sb.append(le);
        sb.append("\"} ");
        sb.append(valueCount);
        sb.append("\n");

    }

    /**
     * see <a href="https://prometheus.io/docs/instrumenting/exposition_formats/">Exposition Formats</a> .
     */
    private void buildLine(StringBuilder sb, String ruleName, long value, String labelName, int labelValue, String postFix) {
        String prometheusName = prometheusCompatibleName("membrane_" + postFix);
        if (sb.isEmpty()) {
            sb.append("# TYPE ");
            sb.append(prometheusName);
            sb.append(" counter\n");
        }

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",");
        sb.append(prometheusCompatibleName(labelName));
        sb.append("=\"");
        sb.append(labelValue);
        sb.append("\"} ");
        sb.append(value);
        sb.append("\n");
    }

    final ConcurrentHashMap<String, String> names = new ConcurrentHashMap<>();
    // see https://prometheus.io/docs/concepts/data_model/
    final Pattern ILLEGAL_FIRST_CHAR = Pattern.compile("^[^a-zA-Z_:]");
    final Pattern ILLEGAL_CHARS = Pattern.compile("[^a-zA-Z0-9_:]");

    private String prometheusCompatibleName(String name) {
        String result = names.get(name);
        if (result != null)
            return result;

        result = prettyPrint(name);

        result = ILLEGAL_CHARS.matcher(result).replaceAll("_");
        result = ILLEGAL_FIRST_CHAR.matcher(result).replaceAll("_");
        names.put(name, result);

        return result;
    }

    private String prettyPrint(String ruleName) {
        StringBuilder sb = new StringBuilder();
        String[] split = ruleName.toLowerCase().split(" ");
        for (String s : split)
            sb.append(s).append("_");
        sb.setLength(sb.length() - 1);
        return sb.toString();

    }

    @MCAttribute
    public void setBuckets(String buckets) {
        TimeCollector.setBuckets(Arrays.stream(buckets
                        .replaceAll("\\s+", "")
                        .split(","))
                .map(Long::parseLong)
                .collect(toList()));
    }

    public String getBuckets() {
        return joinByComma(TimeCollector.getBuckets().stream().map(Object::toString).collect(toList()));
    }

    @Override
    public String getShortDescription() {
        return "Returns Membrane's internal metrics in the Prometheus format.";
    }

    @Override
    public String getLongDescription() {
        return "Returns Membrane's internal metrics in the <a href=\"https://prometheus.io/docs/concepts/data_model/\">Prometheus</a> format.";
    }
}
