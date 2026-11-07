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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.*;

/**
 * The filters of {@code GET /exchanges}, evaluated against exchanges held in memory.
 */
record ExchangeFilter(String method, Integer statusCode, Integer statusClass, String proxy, String client,
                      String server, String reqContentType, String respContentType, Long from, Long to,
                      Integer minDuration, String text, List<String[]> headers, String body,
                      boolean useXForwardedForAsClientAddr) {

    private static final Pattern STATUS = Pattern.compile("([1-5])(\\d\\d|xx)");

    static ExchangeFilter of(ApiRequest req, boolean useXForwardedForAsClientAddr) {
        Integer statusCode = null;
        Integer statusClass = null;
        final var status = req.param("statusCode");
        if (status.isPresent()) {
            final var m = STATUS.matcher(status.get());
            if (!m.matches())
                throw badRequest("Query parameter 'statusCode' must be a status code like 404 or a class like 4xx.");
            if ("xx".equals(m.group(2)))
                statusClass = Integer.parseInt(m.group(1));
            else
                statusCode = Integer.parseInt(status.get());
        }
        final var headers = new ArrayList<String[]>();
        for (final var h : req.repeated("header")) {
            final var colon = h.indexOf(':');
            if (colon < 1)
                throw badRequest("Query parameter 'header' must look like 'name:value'.");
            headers.add(new String[]{h.substring(0, colon).trim(), h.substring(colon + 1).trim()});
        }
        return new ExchangeFilter(
                req.param("method").orElse(null),
                statusCode,
                statusClass,
                req.param("proxy").orElse(null),
                req.param("client").orElse(null),
                req.param("server").orElse(null),
                req.param("reqContentType").orElse(null),
                req.param("respContentType").orElse(null),
                req.param("from").map(v -> time("from", v)).orElse(null),
                req.param("to").map(v -> time("to", v)).orElse(null),
                req.param("minDuration").isPresent() ? req.intParam("minDuration", 0, 0, Integer.MAX_VALUE) : null,
                req.param("q").orElse(null),
                List.copyOf(headers),
                req.param("body").orElse(null),
                useXForwardedForAsClientAddr);
    }

    /**
     * Whether a filter is used that the legacy query interface of persistent stores cannot express.
     */
    boolean needsInMemoryFiltering() {
        return statusClass != null || from != null || to != null || minDuration != null || !headers.isEmpty() || body != null;
    }

    boolean test(AbstractExchange exc) {
        final var req = exc.getRequest();
        final var res = exc.getResponse();
        return eq(method, req == null ? null : req.getMethod())
               && (statusCode == null || (res != null && res.getStatusCode() == statusCode))
               && (statusClass == null || (res != null && res.getStatusCode() / 100 == statusClass))
               && (proxy == null || (exc.getProxy() != null && (proxy.equals(exc.getProxy().toString()) || proxy.equals(ApisResource.apiId(exc.getProxy())))))
               && eq(client, getClientAddr(useXForwardedForAsClientAddr, exc))
               && eq(server, exc.getServer())
               && eq(reqContentType, req == null ? null : exc.getRequestContentType())
               && eq(respContentType, res == null ? null : exc.getResponseContentType())
               && (from == null || exc.getTime().getTimeInMillis() >= from)
               && (to == null || exc.getTime().getTimeInMillis() <= to)
               && (minDuration == null || duration(exc) >= minDuration)
               && (text == null || matchesText(exc))
               && headers.stream().allMatch(h -> headerContains(req, h) || headerContains(res, h))
               && (body == null || bodyContains(req) || bodyContains(res));
    }

    /**
     * @return 0 unless both the time the request was sent and the time the response was received are recorded
     */
    static long duration(AbstractExchange exc) {
        if (exc.getTimeReqSent() <= 0 || exc.getTimeResReceived() <= 0)
            return 0;
        return Math.max(0, exc.getTimeResReceived() - exc.getTimeReqSent());
    }

    private boolean matchesText(AbstractExchange exc) {
        final Predicate<String> contains = s -> s != null && s.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
        final var req = exc.getRequest();
        return (req != null && (contains.test(req.getMethod()) || contains.test(req.getUri())))
               || contains.test(getClientAddr(useXForwardedForAsClientAddr, exc))
               || contains.test(exc.getServer())
               || (req != null && contains.test(exc.getRequestContentType()))
               || (exc.getResponse() != null && contains.test(exc.getResponseContentType()));
    }

    private static boolean headerContains(Message msg, String[] nameValue) {
        if (msg == null)
            return false;
        for (final var field : msg.getHeader().getAllHeaderFields())
            if (field.getHeaderName().toString().equalsIgnoreCase(nameValue[0])
                && field.getValue() != null
                && field.getValue().toLowerCase(Locale.ROOT).contains(nameValue[1].toLowerCase(Locale.ROOT)))
                return true;
        return false;
    }

    private boolean bodyContains(Message msg) {
        try {
            return msg != null && !msg.isBodyEmpty()
                   && msg.getBodyAsStringDecoded().toLowerCase(Locale.ROOT).contains(body.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean eq(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }

    private static long time(String name, String value) {
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw badRequest("Query parameter '%s' must be an RFC 3339 date-time.".formatted(name));
        }
    }
}
