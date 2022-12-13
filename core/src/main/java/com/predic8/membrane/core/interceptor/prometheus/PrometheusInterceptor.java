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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.rules.TimeCollector;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@MCElement(name = "prometheus")
public class PrometheusInterceptor extends AbstractInterceptor {

    static final Logger LOG = LoggerFactory.getLogger(PrometheusInterceptor.class);
    static volatile boolean issuedDuplicateRuleNameWarning = false;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Context ctx = new Context();
        buildPrometheusStyleResponse(ctx);
        exc.setResponse(Response.ok(ctx.sb.toString()).header(Header.CONTENT_TYPE, "text/plain; version=0.0.4").build());
        return Outcome.RETURN;
    }

    private class Context {
        StringBuilder sb = new StringBuilder();

        List<StringBuilder> dynamic = new ArrayList<>();

        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        StringBuilder s3 = new StringBuilder();
        StringBuilder s4 = new StringBuilder();
        StringBuilder s5 = new StringBuilder();
        StringBuilder s6 = new StringBuilder();
        StringBuilder s7 = new StringBuilder();
        StringBuilder s8 = new StringBuilder();
        StringBuilder s9 = new StringBuilder();
        StringBuilder s10 = new StringBuilder();

        HashSet<String> seenRules = new HashSet<>();

        private StringBuilder getNew() {
            StringBuilder b = new StringBuilder();
            dynamic.add(b);
            return b;
        }

        private void reset() {
            s1.setLength(0);
            s2.setLength(0);
            s3.setLength(0);
            s4.setLength(0);
            s5.setLength(0);
            s6.setLength(0);
            s7.setLength(0);
            s8.setLength(0);
            s9.setLength(0);
            s10.setLength(0);

            dynamic.forEach(s -> s.setLength(0));
        }

        private void resetAll() {
            sb.setLength(0);
            seenRules.clear();
        }

        private void collect() {
            sb.append(s1);
            sb.append(s2);
            sb.append(s3);
            sb.append(s4);
            sb.append(s5);
            sb.append(s6);
            sb.append(s7);
            sb.append(s8);
            sb.append(s9);
            sb.append(s10);

            dynamic.forEach(s -> sb.append(s));
        }
    }

    private void buildPrometheusStyleResponse(Context ctx) {
        ctx.resetAll();
        ctx.reset();
        for (Rule r : router.getRuleManager().getRules()) {
            if (!ctx.seenRules.add(prometheusCompatibleName(r.getName()))) {
                // the prometheus format is not allowed to contain the same metric more than once
                if (issuedDuplicateRuleNameWarning)
                    continue;
                LOG.warn("The prometheus interceptor detected the same rule name more than once: " + r.getName());
                issuedDuplicateRuleNameWarning = true;
                continue;
            }

            buildStatuscodeLines(ctx, r);
            buildBuckets(ctx, r);
            buildActive(ctx, r);

            if (r.isActive()) {
                SSLContext sslib = r.getSslInboundContext();
                if (sslib != null)
                    buildSSLLines(ctx, r, sslib);
            }

            if (r instanceof OpenAPIProxy) {
                buildOpenAPIValidatorLines(ctx, (OpenAPIProxy) r);
            }

        }
        buildDuplicateRuleNameWarning(ctx, issuedDuplicateRuleNameWarning);
        ctx.collect();

    }

    private void buildOpenAPIValidatorLines(Context ctx, OpenAPIProxy proxy) {
        for (Map.Entry<ValidationStatsKey, Integer> e : proxy.getValidationStatisticCollector().getStats().entrySet()) {
            buildLine(ctx.s10, proxy.getName(), e.getValue(), e.getKey().getLabels(), "openapi_validation");
        }
    }

    private StringBuilder buildLine(StringBuilder sb, String ruleName, long value, Map<String, String> labels, String postFix) {
        String prometheusName = prometheusCompatibleName("membrane_" + postFix);
        if (sb.length() == 0) {
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
        return sb;
    }

    private void buildSSLLines(Context ctx, Rule r, SSLContext sslib) {
        boolean hasKeyAndCert = sslib.hasKeyAndCertificate();
        buildSSLLine(ctx.s7, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_haskeyandcert", hasKeyAndCert ? 1 : 0);
        if (hasKeyAndCert) {
            buildSSLLine(ctx.s8, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_validfrom_ms", sslib.getValidFrom());
            buildSSLLine(ctx.s9, r.getName(), sslib.getPrometheusContextTypeName(), "ssl_validuntil_ms", sslib.getValidUntil());
        }
    }

    private void buildSSLLine(StringBuilder sb, String ruleName, String prometheusContextTypeName, String metric, long value) {
        String prometheusName = prometheusCompatibleName("membrane_" + metric);

        if (sb.length() == 0) {
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
        ctx.s6.append("# TYPE membrane_duplicate_rule_name gauge\n");

        buildLine(ctx.s6, "duplicate_rule_name", hasDuplicateRuleName ? 1 : 0);
    }

    private void buildActive(Context ctx, Rule r) {
        if (ctx.s6.length() == 0)
            ctx.s6.append("# TYPE membrane_rule_active gauge\n");

        buildBucketLine(ctx.s6, r.getName(), "rule_active", r.isActive() ? 1 : 0);
    }

    private void buildBuckets(Context ctx, Rule rule) {
        rule.getStatisticCollector().getTimeStatisticsByStatusCodeRange().forEach((code, tc) -> {
            tc.getTrackedTimes().forEach((name, tt) -> {
                if (tt.isEmpty())
                    return;

                StringBuilder sb = ctx.getNew();
                tt.forEach((le, count) -> {
                    if (le.equals("SUM") || le.equals("COUNT"))
                        return;

                    buildBucketLine(sb, rule.getName(), le, count, name);
                });
                buildBucketLine(sb, rule.getName(), name + "_sum", tt.get("SUM"));
                buildBucketLine(sb, rule.getName(), name + "_count", tt.get("COUNT"));
            });
        });
    }

    private void buildStatuscodeLines(Context ctx, Rule rule) {
        Map<Integer, StatisticCollector> stats = rule.getStatisticCollector().getStatisticsByStatusCodes();

        for (Integer code : stats.keySet()) {
            buildLine(ctx.s1, rule.getName(), stats.get(code).getCount(), "code", code, "count");
            buildLine(ctx.s2, rule.getName(), stats.get(code).getGoodCount(), "code", code, "good_count");
            buildLine(ctx.s3, rule.getName(), stats.get(code).getGoodTotalTime(), "code", code, "good_time");
            buildLine(ctx.s4, rule.getName(), stats.get(code).getGoodTotalBytesSent(), "code", code, "good_bytes_req_body");
            buildLine(ctx.s5, rule.getName(), stats.get(code).getGoodTotalBytesReceived(), "code", code, "good_bytes_res_body");
        }

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

    private void buildBucketLine(StringBuilder sb, String ruleName, String le, long valueCount, String infix) {
        String prometheusName = prometheusCompatibleName("membrane_" + infix + "_bucket");
        if (sb.length() == 0) {
            sb.append("# TYPE ");
            sb.append(prometheusName);
            sb.append(" histogram\n");
        }

        sb.append(prometheusName);
        sb.append("{rule=\"");
        sb.append(prometheusCompatibleName(ruleName));
        sb.append("\",le=\"");
        sb.append(le);
        sb.append("\"} ");
        sb.append(valueCount);
        sb.append("\n");

    }

    /**
     * see https://prometheus.io/docs/instrumenting/exposition_formats/ .
     */
    private StringBuilder buildLine(StringBuilder sb, String ruleName, long value, String labelName, int labelValue, String postFix) {
        String prometheusName = prometheusCompatibleName("membrane_" + postFix);
        if (sb.length() == 0) {
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
        return sb;
    }

    ConcurrentHashMap<String, String> names = new ConcurrentHashMap<>();
    // see https://prometheus.io/docs/concepts/data_model/
    Pattern ILLEGAL_FIRST_CHAR = Pattern.compile("^[^a-zA-Z_:]");
    Pattern ILLEGAL_CHARS = Pattern.compile("[^a-zA-Z0-9_:]");

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

    public List<Long> getBuckets() {
        return TimeCollector.getBuckets();
    }

    @MCAttribute
    public void setBuckets(String buckets) {
        TimeCollector.setBuckets(Arrays.stream(buckets
                        .replaceAll("\\s+", "")
                        .split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList()));
    }
}
