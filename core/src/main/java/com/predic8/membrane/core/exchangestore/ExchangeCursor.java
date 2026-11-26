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

import static com.predic8.membrane.core.exchangestore.ExchangeSortKey.TIME;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

/**
 * Position of an exchange in the exchange list sorted by a {@link ExchangeSortKey}: its value of the sort
 * field, then time. Exchange ids are not ordered, so the id only breaks ties between exchanges of the same
 * millisecond. Because a cursor is compared by value, it stays valid after its exchange has been evicted
 * from the store.
 *
 * @param value the exchange's value of the sort field, {@code null} when sorting by time or when it has none
 */
public record ExchangeCursor(long time, long id, Comparable<?> value) {

    private static final Comparator<Comparable<Object>> VALUES = nullsFirst(naturalOrder());

    private static final Comparator<ExchangeCursor> ASCENDING =
            ((Comparator<ExchangeCursor>) (a, b) -> compareValues(a.value, b.value))
                    .thenComparingLong(ExchangeCursor::time)
                    .thenComparingLong(ExchangeCursor::id);

    public ExchangeCursor(long time, long id) {
        this(time, id, null);
    }

    public static ExchangeCursor of(AbstractExchange exc) {
        return of(exc, TIME);
    }

    public static ExchangeCursor of(AbstractExchange exc, ExchangeSortKey key) {
        return new ExchangeCursor(exc.getTime() != null ? exc.getTime().getTimeInMillis() : 0, exc.getId(), key.valueOf(exc));
    }

    /**
     * @param cursor as produced by {@link #encode()} for the same sort key
     * @throws InvalidExchangeQueryException if the cursor is malformed
     */
    public static ExchangeCursor parse(String cursor, ExchangeSortKey key) {
        final var parts = cursor.split("_", 3);
        if (parts.length < 2 || (key == TIME && parts.length == 3))
            throw new InvalidExchangeQueryException("Invalid cursor '%s'.".formatted(cursor));
        try {
            return new ExchangeCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                    parts.length == 3 ? key.parse(parts[2]) : null);
        } catch (NumberFormatException e) {
            throw new InvalidExchangeQueryException("Invalid cursor '%s'.".formatted(cursor));
        }
    }

    static Comparator<ExchangeCursor> order(boolean descending) {
        return descending ? ASCENDING.reversed() : ASCENDING;
    }

    static Comparator<AbstractExchange> exchangeOrder(ExchangeSortKey key, boolean descending) {
        return Comparator.comparing(exc -> of(exc, key), order(descending));
    }

    @SuppressWarnings("unchecked")
    private static int compareValues(Comparable<?> a, Comparable<?> b) {
        return VALUES.compare((Comparable<Object>) a, (Comparable<Object>) b);
    }

    /**
     * @return {@code <time>_<id>}, followed by {@code _<value>} if the cursor has a value
     */
    public String encode() {
        return value == null ? time + "_" + id : "%d_%d_%s".formatted(time, id, value);
    }
}
