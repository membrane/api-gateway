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

import java.util.Comparator;

import static java.util.Comparator.comparingLong;

/**
 * Position of an exchange in the time-sorted exchange list. Exchange ids are not ordered, so the id only
 * breaks ties between exchanges of the same millisecond. Because a cursor is compared by value, it stays
 * valid after its exchange has been evicted from the store.
 */
public record ExchangeCursor(long time, long id) {

    private static final Comparator<ExchangeCursor> ASCENDING =
            comparingLong(ExchangeCursor::time).thenComparingLong(ExchangeCursor::id);

    public static ExchangeCursor of(AbstractExchange exc) {
        return new ExchangeCursor(exc.getTime() != null ? exc.getTime().getTimeInMillis() : 0, exc.getId());
    }

    /**
     * @param cursor as produced by {@link #encode()}
     * @throws InvalidExchangeQueryException if the cursor is malformed
     */
    public static ExchangeCursor parse(String cursor) {
        final var parts = cursor.split("_", 2);
        if (parts.length != 2)
            throw new InvalidExchangeQueryException("Invalid cursor '%s'.".formatted(cursor));
        try {
            return new ExchangeCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new InvalidExchangeQueryException("Invalid cursor '%s'.".formatted(cursor));
        }
    }

    static Comparator<ExchangeCursor> order(boolean descending) {
        return descending ? ASCENDING.reversed() : ASCENDING;
    }

    static Comparator<AbstractExchange> exchangeOrder(boolean descending) {
        return Comparator.comparing(ExchangeCursor::of, order(descending));
    }

    public String encode() {
        return time + "_" + id;
    }
}
