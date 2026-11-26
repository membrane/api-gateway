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

import com.predic8.membrane.core.exchange.AbstractExchange;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Sort fields that support cursor paging. Besides time, an exchange's cursor carries its value of the sort
 * field, so pages stay stable for every sort. Exchanges without a value (e.g. no response yet) sort first in
 * ascending order.
 */
public enum ExchangeSortKey {
    TIME("time", false, exc -> null),
    API("api", false, exc -> exc.getProxy() == null ? null : exc.getProxy().toString()),
    STATUS_CODE("statusCode", true, exc -> exc.getResponse() == null ? null : (long) exc.getResponse().getStatusCode()),
    METHOD("method", false, exc -> exc.getRequest().getMethod()),
    PATH("path", false, exc -> exc.getRequest().getUri()),
    CLIENT("client", false, AbstractExchange::getRemoteAddr),
    SERVER("server", false, AbstractExchange::getServer),
    DURATION("duration", true, exc -> exc.getResponse() == null ? null : exc.getTimeResReceived() - exc.getTimeReqSent());

    private final String parameterValue;
    private final boolean numeric;
    private final Function<AbstractExchange, Comparable<?>> extractor;

    ExchangeSortKey(String parameterValue, boolean numeric, Function<AbstractExchange, Comparable<?>> extractor) {
        this.parameterValue = parameterValue;
        this.numeric = numeric;
        this.extractor = extractor;
    }

    /**
     * @param sort value of the {@code sort} query parameter
     * @return empty if cursors don't support sorting by this field
     */
    public static Optional<ExchangeSortKey> find(String sort) {
        return Arrays.stream(values()).filter(key -> key.parameterValue.equals(sort)).findFirst();
    }

    public String getParameterValue() {
        return parameterValue;
    }

    Comparable<?> valueOf(AbstractExchange exc) {
        return extractor.apply(exc);
    }

    /**
     * @throws InvalidExchangeQueryException if a numeric value is malformed
     */
    Comparable<?> parse(String value) {
        if (!numeric)
            return value;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidExchangeQueryException("Invalid %s '%s' in cursor.".formatted(parameterValue, value));
        }
    }
}
