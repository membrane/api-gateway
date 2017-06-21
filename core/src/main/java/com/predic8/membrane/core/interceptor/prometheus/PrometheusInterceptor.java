package com.predic8.membrane.core.interceptor.prometheus;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.StatisticCollector;

import java.util.Map;
import java.util.regex.Pattern;

@MCElement(name = "prometheus")
public class PrometheusInterceptor extends AbstractInterceptor {


    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        StringBuilder sb = new StringBuilder();
        buildPrometheusStyleResponse(sb);
        exc.setResponse(Response.ok(sb.toString()).header(Header.CONTENT_TYPE, MimeType.TEXT_PLAIN_UTF8).build());
        return Outcome.RETURN;
    }

    private void buildPrometheusStyleResponse(StringBuilder sb) {
        for (Rule r : router.getRuleManager().getRules())
            buildStatuscodeLines(sb, r);
    }

    private StringBuilder buildStatuscodeLines(StringBuilder sb, Rule rule) {
        Map<Integer, StatisticCollector> stats = rule.getStatisticsByStatusCodes();

        for (Integer code : stats.keySet()) {
            buildLine(sb, rule.getName(), String.valueOf(stats.get(code).getCount()), "code", code, "count");
            buildLine(sb, rule.getName(), String.valueOf(stats.get(code).getMinTime()), "code", code, "min");
            buildLine(sb, rule.getName(), String.valueOf(stats.get(code).getMaxTime()), "code", code, "max");
            buildLine(sb, rule.getName(), String.valueOf(stats.get(code).getAvgTime()), "code", code, "avg");
        }

        return sb;
    }

    private StringBuilder buildLine(StringBuilder sb, String ruleName, Object value, Object... keys) {
        sb.append(toLowerCaseUnderscore(replaceColonWithUnknown(ruleName)));
        for (Object key : keys)
            sb.append("_").append(String.valueOf(key));
        sb.append(" ").append(String.valueOf(value)).append(System.lineSeparator());

        return sb;
    }

    private String replaceColonWithUnknown(String ruleName) {
        if (ruleName.startsWith(":"))
            return ruleName.replaceFirst(Pattern.quote(":"), "unknown_");
        return ruleName;
    }

    private String toLowerCaseUnderscore(String ruleName) {
        StringBuilder sb = new StringBuilder();
        String[] split = ruleName.toLowerCase().split(" ");
        for (String s : split)
            sb.append(s).append("_");
        sb.setLength(sb.length() - 1);
        return sb.toString();

    }
}
