/* Copyright 2013 predic8 GmbH, www.predic8.com

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

import java.util.Comparator;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.exchangestore.ClientStatistics;
import com.predic8.membrane.core.interceptor.statistics.PropertyComparator;
import com.predic8.membrane.core.rules.*;

public class ComparatorFactory {

    public static Comparator<AbstractExchange> getAbstractExchangeComparator(String propName, String order) {
        if ("statusCode".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getResponse().getStatusCode());
        } else if ("proxy".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getRule().toString());
        } else if ("method".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getRequest().getMethod());
        } else if ("path".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getRequest().getUri());
        } else if ("client".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getRemoteAddr);
        } else if ("server".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getServer);
        } else if ("reqContentType".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getRequestContentType);
        } else if ("reqContentLength".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getRequestContentLength);
        } else if ("respContentType".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getResponseContentType);
        } else if ("respContentLength".equals(propName)) {
            return new PropertyComparator<>(order, AbstractExchange::getResponseContentLength);
        } else if ("duration".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getTimeResReceived() - exc.getTimeReqSent());
        } else if ("msgFilePath".equals(propName)) {
            return new PropertyComparator<>(order, exc -> exc.getRequest().getMethod());
        } else if ("time".equals(propName)) {
            return new PropertyComparator<>(order, ExchangesUtil::getTime);
        }

        throw new IllegalArgumentException("AbstractExchange can't be compared using property [" + propName + "]");
    }

    public static Comparator<AbstractServiceProxy> getAbstractServiceProxyComparator(final String propName, String order) {
        return switch (propName) {
            case "listenPort" -> new PropertyComparator<>(order, p -> p.getKey().getPort());
            case "virtualHost" -> new PropertyComparator<>(order, p -> p.getKey().getHost());
            case "method" -> new PropertyComparator<>(order, p -> p.getKey().getMethod());
            case "path" -> new PropertyComparator<>(order, p -> p.getKey().getPath());
            case "targetHost" -> new PropertyComparator<>(order, AbstractServiceProxy::getTargetHost);
            case "targetPort" -> new PropertyComparator<>(order, AbstractServiceProxy::getTargetPort);
            case "count" -> new PropertyComparator<>(order, p -> p.getStatisticCollector().getCount());
            case "name" -> new PropertyComparator<>(order, AbstractProxy::toString);
            default ->
                    throw new IllegalArgumentException("AbstractServiceProxy can't be compared using property [" + propName + "]");
        };
    }

    public static Comparator<ClientStatistics> getClientStatisticsComparator(String propName,
                                                                             String order) {
        return switch (propName) {
            case "name" -> new PropertyComparator<>(order, ClientStatistics::getClient);
            case "count" -> new PropertyComparator<>(order, ClientStatistics::getCount);
            case "min" -> new PropertyComparator<>(order, ClientStatistics::getMinDuration);
            case "max" -> new PropertyComparator<>(order, ClientStatistics::getMaxDuration);
            case "avg" -> new PropertyComparator<>(order, ClientStatistics::getAvgDuration);
            default -> throw new IllegalArgumentException("ClientsStatistics can't be compared using property [" + propName + "]");
        };
    }
}
