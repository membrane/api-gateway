package com.predic8.membrane.core.exchangestore;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ElasticSearchExchangeStore extends AbstractExchangeStore {

    static Logger log = LoggerFactory.getLogger(ElasticSearchExchangeStore.class);
    String elasticSearchBasePath = null;
    int updateIntervalMs = 1000;
    Map<Long,AbstractExchange> shortTermMemoryForBatching = new HashMap<>();
    Thread updateJob;

    String host = "localhost";
    int port = 9200;

    @Override
    public void init() {
        super.init();

        updateJob = new Thread(() -> {
            while(true) {
                try {
                    List<AbstractExchange> exchanges;
                    synchronized (shortTermMemoryForBatching){
                        exchanges = shortTermMemoryForBatching.values().stream().collect(Collectors.toList());
                        shortTermMemoryForBatching.clear();
                    }
                    asyncSendToElasticSearch(exchanges);

                    Thread.sleep(updateIntervalMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateJob.start();
    }

    private void asyncSendToElasticSearch(List<AbstractExchange> exchanges) {

    }

    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        if(elasticSearchBasePath == null)
            initElasticSearchBasePath(exc);

        AbstractExchange excCopy = null;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = cleanSnapshot(exc.createSnapshot());
                addForElasticSearch(excCopy);
            }
            else {
                excCopy = cleanSnapshot(Exchange.updateCopy(exc, getExchangeById((int) exc.getId())));
                addForElasticSearch(excCopy);
            }
            addObservers(exc, excCopy, flow);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initElasticSearchBasePath(AbstractExchange exc) {
        elasticSearchBasePath = "/membrane/" + exc.getPublicUrl() + "/exchanges/";
    }

    private void addForElasticSearch(AbstractExchange exc) {
        synchronized (shortTermMemoryForBatching){
            shortTermMemoryForBatching.put(exc.getId(),exc);
        }
    }

    private void addObservers(AbstractExchange exc, AbstractExchange excCopy, Interceptor.Flow flow) throws Exception {
        Message msg = null;
        if(flow == Interceptor.Flow.REQUEST) {
            msg = exc.getRequest();
        }
        else
            msg = exc.getResponse();

        msg.addObserver(new MessageObserver() {
            @Override
            public void bodyRequested(AbstractBody body) {

            }

            @Override
            public void bodyComplete(AbstractBody body) {
                try {
                    addForElasticSearch(cleanSnapshot(Exchange.updateCopy(exc,excCopy)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void setExchangeFinished() {
                try {
                    addForElasticSearch(cleanSnapshot(Exchange.updateCopy(exc,excCopy)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        addForElasticSearch(cleanSnapshot(Exchange.updateCopy(exc,excCopy)));
    }

    public <T extends AbstractExchange> T cleanSnapshot(T snapshot){
        if(snapshot.getRequest() != null)
            if(snapshot.getRequest().getHeader().isBinaryContentType())
                snapshot.getRequest().setBody(new EmptyBody());
        if(snapshot.getResponse() != null)
            if(snapshot.getResponse().getHeader().isBinaryContentType())
                snapshot.getResponse().setBody(new EmptyBody());

        return snapshot;
    }

    @Override
    public AbstractExchange getExchangeById(int id) {
        return null;
    }

    @Override
    public void remove(AbstractExchange exchange) {

    }

    @Override
    public void removeAllExchanges(Rule rule) {

    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {

    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        return new AbstractExchange[0];
    }

    @Override
    public int getNumberOfExchanges(RuleKey ruleKey) {
        return 0;
    }

    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        return null;
    }

    @Override
    public Object[] getAllExchanges() {
        return new Object[0];
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        return null;
    }
}
