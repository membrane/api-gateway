/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

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

import java.util.List;

public class ExchangeQueryResult {
    List<AbstractExchange> exchanges;
    int count;
    long lastModified;

    public ExchangeQueryResult(List<AbstractExchange> exchanges, int count, long lastModified) {
        this.exchanges = exchanges;
        this.count = count;
        this.lastModified = lastModified;
    }

    public List<AbstractExchange> getExchanges() {
        return exchanges;
    }

    public int getCount() {
        return count;
    }

    public long getLastModified() {
        return lastModified;
    }
}
