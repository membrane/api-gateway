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
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.util.ComparatorFactory.getAbstractExchangeComparator;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComparatorFactoryTest {

    /**
     * An exchange still waiting for its response sorts before the others in ascending and after them in descending
     * order, instead of failing the sort.
     */
    @ParameterizedTest
    @CsvSource({"statusCode, asc, 0", "statusCode, desc, 2", "respContentLength, asc, 0", "respContentLength, desc, 2"})
    void exchangesWithoutResponseCanBeSorted(String property, String order, int positionOfPending) throws Exception {
        final var pending = new Exchange(null);
        final var ok = withResponse(Response.ok("a").build());
        final var notFound = withResponse(Response.notFound().body("bb").build());
        final var exchanges = new ArrayList<AbstractExchange>(List.of(ok, pending, notFound));

        exchanges.sort(getAbstractExchangeComparator(property, order));

        assertEquals(pending, exchanges.get(positionOfPending));
    }

    private static Exchange withResponse(Response response) {
        final var exchange = new Exchange(null);
        exchange.setResponse(response);
        return exchange;
    }
}
