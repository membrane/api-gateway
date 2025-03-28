/* Copyright 2018 predic8 GmbH, www.predic8.com

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

import com.google.common.cache.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;


public abstract class AbstractPersistentExchangeStore extends AbstractExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(AbstractPersistentExchangeStore.class);

    int updateIntervalMs = 1000;

    final Map<Long, AbstractExchangeSnapshot> shortTermMemoryForBatching = new HashMap<>();
    final Cache<Long, AbstractExchangeSnapshot> cacheToWait = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();

    protected long startTime;
    boolean init = false;
    protected int maxBodySize = 100000;
    protected BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy = BodyCollectingMessageObserver.Strategy.TRUNCATE;
    volatile boolean updateThreadWorking;

    @Override
    public void init(Router router) {
        super.init(router);

        startTime = System.nanoTime();

        Thread updateJob = new Thread(() -> {
            while (true) {
                try {
                    List<AbstractExchangeSnapshot> exchanges;
                    updateThreadWorking = true;
                    synchronized (shortTermMemoryForBatching) {
                        exchanges = new ArrayList<>(shortTermMemoryForBatching.values());
                        shortTermMemoryForBatching.values().forEach(exc -> cacheToWait.put(exc.getId(), exc));
                        shortTermMemoryForBatching.clear();
                    }
                    if (!exchanges.isEmpty()) {
                        writeToStore(exchanges);
                    } else {
                        updateThreadWorking = false;
                        Thread.sleep(updateIntervalMs);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        updateJob.start();
        init = true;
    }

    protected abstract void writeToStore(List<AbstractExchangeSnapshot> exchanges);


    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        AbstractExchangeSnapshot excCopy;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = new DynamicAbstractExchangeSnapshot(exc, flow, this::addForStorage, bodyExceedingMaxSizeStrategy, maxBodySize);
                addForStorage(excCopy);
            } else {
                excCopy = getExchangeDtoById((int) exc.getId());
                DynamicAbstractExchangeSnapshot.addObservers(exc, excCopy, this::addForStorage, flow);
                excCopy = excCopy.updateFrom(exc, flow);
                addForStorage(excCopy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param exc To add to batch cache
     */
    protected void addForStorage(AbstractExchangeSnapshot exc) {
        synchronized (shortTermMemoryForBatching) {
            shortTermMemoryForBatching.put(exc.getId(), exc);
        }
    }

    @Override
    public abstract void collect(ExchangeCollector collector);

    public AbstractExchangeSnapshot getExchangeDtoById(int id) {
        Long idBox = (long) id;
        AbstractExchangeSnapshot memorizedExchangeSnapshot;
        synchronized (shortTermMemoryForBatching) {
            memorizedExchangeSnapshot = shortTermMemoryForBatching.get(idBox);
        }
        if (memorizedExchangeSnapshot != null)
            return memorizedExchangeSnapshot;
        AbstractExchangeSnapshot cachedExchangeSnapshot = cacheToWait.getIfPresent(idBox);
        if (cachedExchangeSnapshot != null)
            return cachedExchangeSnapshot;

        return getFromStoreById(id);
    }

    public abstract AbstractExchangeSnapshot getFromStoreById(long id);


    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        StatisticCollector statistics = new StatisticCollector(false);
        List<AbstractExchange> exchangesList = Arrays.asList(getExchanges(ruleKey));
        if (exchangesList.isEmpty())
            return statistics;

        for (AbstractExchange abstractExchange : exchangesList) statistics.collectFrom(abstractExchange);

        return statistics;
    }

    @Override
    public Object[] getAllExchanges() {
        return getAllExchangesAsList().toArray();
    }

    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    @MCAttribute
    public void setUpdateIntervalMs(int updateIntervalMs) {
        this.updateIntervalMs = updateIntervalMs;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    /**
     * @default 100000
     */
    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public BodyCollectingMessageObserver.Strategy getBodyExceedingMaxSizeStrategy() {
        return bodyExceedingMaxSizeStrategy;
    }

    /**
     * @description The strategy to use (TRUNCATE or ERROR) when a HTTP message body is larger than the <tt>maxBodySize</tt>.
     * @default TRUNCATE
     */
    @MCAttribute
    public void setBodyExceedingMaxSizeStrategy(BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy) {
        this.bodyExceedingMaxSizeStrategy = bodyExceedingMaxSizeStrategy;
    }
}