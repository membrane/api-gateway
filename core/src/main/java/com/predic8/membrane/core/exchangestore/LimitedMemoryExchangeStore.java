/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description Stores exchange objects in-memory until a memory threshold is reached. When the threshold is reached and new exchanges arrive then old exchanges will be dropped (starting from oldest ascending) until the exchange can be stored. The LimitedMemoryExchangeStore is the default ExchangeStore Membrane uses.
 */
@MCElement(name="limitedMemoryExchangeStore")
public class LimitedMemoryExchangeStore extends AbstractExchangeStore {

	private static Logger log = LoggerFactory.getLogger(LimitedMemoryExchangeStore.class);

	private int maxSize = 1000000;
	private int maxBodySize = 100000;
	private int currentSize;
	private BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy = BodyCollectingMessageObserver.Strategy.TRUNCATE;

	/**
	 * EVERY time that exchanges or inflight is changed, modify() MUST be called afterwards
	 */
	private final Queue<AbstractExchange> exchanges = new LinkedList<>();
	private final Queue<AbstractExchange> inflight = new LinkedList<>();

	private long lastModification = System.currentTimeMillis();

	public void snap(final AbstractExchange exc, final Flow flow) {
		newSnap(exc, flow);
	}

	private void newSnap(AbstractExchange exc, Flow flow) {
		try {
            if (flow == Flow.REQUEST) {
				AbstractExchange excCopy = snapInternal(exc, flow);

				if (exc.getRequest() != null)
					excCopy.setRequest(exc.getRequest().createSnapshot(new Runnable() {
						@Override
						public void run() {
							synchronized (LimitedMemoryExchangeStore.this) {
								currentSize += - excCopy.resetHeapSizeEstimation() + excCopy.getHeapSizeEstimation();
								modify();
							}
						}
					}, bodyExceedingMaxSizeStrategy, maxBodySize));

				exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
					@Override
					public void setExchangeFinished() {
						try {
							snapInternal(exc, Flow.RESPONSE);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			} else {
				AbstractExchange excCopy = snapInternal(exc, flow);

				if (exc.getResponse() != null)
					excCopy.setResponse(exc.getResponse().createSnapshot(new Runnable() {
						@Override
						public void run() {
							currentSize += - excCopy.resetHeapSizeEstimation() + excCopy.getHeapSizeEstimation();
							modify();
						}
					}, bodyExceedingMaxSizeStrategy, maxBodySize));

				modify();
			}

        } catch (Exception e) {
			log.warn("exception during snapshotting: ", e);
            throw new RuntimeException(e);
        }

	}

	private synchronized AbstractExchange snapInternal(AbstractExchange orig, Flow flow) throws Exception {
		AbstractExchange exc = getExchangeById(orig.getId());

		if (exc == null) {
			exc = orig.createSnapshot(null, null, 0);
			AbstractExchange exc2 = exc;
			exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
				@Override
				public void addRequest(Request request) {
					currentSize += - exc2.resetHeapSizeEstimation() + exc2.getHeapSizeEstimation();
				}

				@Override
				public void addResponse(Response response) {
					currentSize += - exc2.resetHeapSizeEstimation() + exc2.getHeapSizeEstimation();
				}
			});
		}

		makeSpaceIfNeeded(exc);

		if (flow == Flow.REQUEST) {
			if (inflight.add(exc))
				currentSize += exc.getHeapSizeEstimation();
		} else {
			if (inflight.remove(exc))
				currentSize -= exc.getHeapSizeEstimation();
			if (!exchanges.contains(exc)) {
				exchanges.add(exc);
				currentSize += exc.getHeapSizeEstimation();
			}
			Exchange.updateCopy(orig, exc, null, null, 0);
		}

		modify();
		return exc;
	}

	public synchronized void remove(AbstractExchange exc) {
		exchanges.remove(exc);
		modify();
	}

	public synchronized void removeAllExchanges(Rule rule) {
		exchanges.removeAll(getExchangeList(rule.getKey()));
		modify();
	}

	private synchronized List<AbstractExchange> getExchangeList(RuleKey key) {
		List<AbstractExchange> c = new ArrayList<AbstractExchange>();
		for (AbstractExchange exc : inflight) {
			if (exc.getRule().getKey().equals(key)) {
				c.add(exc);
			}
		}
		for(AbstractExchange exc : exchanges) {
			if (exc.getRule().getKey().equals(key)) {
				c.add(exc);
			}
		}
		return c;
	}

	public synchronized AbstractExchange[] getExchanges(RuleKey ruleKey) {
		return getExchangeList(ruleKey).toArray(new AbstractExchange[0]);
	}

	public synchronized int getNumberOfExchanges(RuleKey ruleKey) {
		return getExchangeList(ruleKey).size();
	}

	public synchronized StatisticCollector getStatistics(RuleKey key) {
		StatisticCollector statistics = new StatisticCollector(false);
		List<AbstractExchange> exchangesList = getExchangeList(key);
		if (exchangesList == null || exchangesList.isEmpty())
			return statistics;

		for (int i = 0; i < exchangesList.size(); i++)
			statistics.collectFrom(exchangesList.get(i));

		return statistics;
	}

	public synchronized Object[] getAllExchanges() {
		return exchanges.toArray(new AbstractExchange[0]);
	}

	public synchronized List<AbstractExchange> getAllExchangesAsList() {
		List<AbstractExchange> ret = new LinkedList<AbstractExchange>();

		for (AbstractExchange ex : inflight) {
			Request req = ex.getRequest();
			Exchange newEx = new Exchange(null);
			newEx.setId(ex.getId());
			newEx.setRequest(req);
			newEx.setRule(ex.getRule());
			newEx.setRemoteAddr(ex.getRemoteAddr());
			newEx.setTime(ex.getTime());
			newEx.setTimeReqSent(ex.getTimeReqSent() != 0 ? ex.getTimeReqSent() : ex.getTimeReqReceived());
			newEx.setTimeResReceived(System.currentTimeMillis());
			ret.add(newEx);
		}
		ret.addAll(exchanges);

		return ret;
	}

	public synchronized void removeAllExchanges(AbstractExchange[] candidates) {
		exchanges.removeAll(Arrays.asList(candidates));
		modify();
	}


	@Override
	public synchronized AbstractExchange getExchangeById(long id) {
		return exchanges.stream().filter(exc -> exc.getId() == id).findAny()
				.orElseGet(() ->
						inflight.stream().filter(exc -> exc.getId() == id).findAny()
								.orElse(null));
	}

	@Override
	public synchronized List<? extends ClientStatistics> getClientStatistics() {
		Map<String, ClientStatisticsCollector> clients = new HashMap<String, ClientStatisticsCollector>();

		for (AbstractExchange exc : getAllExchangesAsList()) {
			if (!clients.containsKey(exc.getRemoteAddr())) {
				clients.put(exc.getRemoteAddr(), new ClientStatisticsCollector(exc.getRemoteAddr()));
			}
			clients.get(exc.getRemoteAddr()).collect(exc);
		}
		return new ArrayList<ClientStatistics>(clients.values());
	}

	public synchronized int getCurrentSize() {
		return exchanges.stream().map(abstractExchange -> abstractExchange.getHeapSizeEstimation()).reduce(0,(a,b) -> a+b);
	}

	public synchronized Long getOldestTimeResSent() {
		AbstractExchange exc = exchanges.peek();
		return exc == null ? null : exc.getTimeResSent();
	}

	private synchronized void makeSpaceIfNeeded(AbstractExchange exc) {
		while (!hasEnoughSpace(exc)) {
			AbstractExchange removedExc = exchanges.poll();
			if (removedExc == null)
				break;
			currentSize -= removedExc.getHeapSizeEstimation();
		}
	}

	private boolean hasEnoughSpace(AbstractExchange exc) {
		return exc.getHeapSizeEstimation()+getCurrentSize() <= maxSize;
	}

	/**
	 * @description Threshold limit in bytes until old exchanges are dropped.
	 * @example 1048576<i>(1Mb)</i>
	 * @default 1000000
	 */
	public int getMaxSize() {
		return maxSize;
	}

	static final int additionalMemoryToAddInMb = 100;

	@MCAttribute
	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
		if(this.maxSize > (Runtime.getRuntime().totalMemory()-additionalMemoryToAddInMb*1024*1024))
			showWarningNotEnoughMemory();
	}

	private void showWarningNotEnoughMemory() {

		String separator = "=========================================================================================";
		log.warn(separator);
		log.warn(separator);
		log.warn("You current LimitedMemoryExchangeStore max size is near the max available JVM heap space.");
		log.warn("LimitedMemoryExchangeStore max size: " + formatTwoDecimals(getLmesMaxSizeInMb()) + "mb");
		log.warn("Java Virtual Machine heap size: " + formatTwoDecimals(getJvmHeapSizeInMb()) + "mb");
		log.warn("Suggestion: add \"-Xmx"+Math.round(getLmesMaxSizeInMb()+additionalMemoryToAddInMb+1)+"m\" as additional parameter in the Membrane starter script");
		log.warn(separator);
		log.warn(separator);
	}

	private float getJvmHeapSizeInMb() {
		return ((float)Runtime.getRuntime().totalMemory()/1024)/1024;
	}


	private float getLmesMaxSizeInMb() {
		return ((float)(maxSize) /1024)/1024;
	}

	private String formatTwoDecimals(float number){
		DecimalFormat formatter = new DecimalFormat("#.##");
		return formatter.format(number);
	}

	private synchronized void modify() {
		lastModification = System.currentTimeMillis();
		notifyAll();
	}

	@Override
	public synchronized long getLastModified() {
		return lastModification;
	}

	@Override
	public synchronized void waitForModification(long lastKnownModification) throws InterruptedException {
		for (;;) {
			if (lastKnownModification < this.lastModification) {
				return;
			}
			// lastKnownModification >= this.lastModification:
			wait();
		}
	}

	public int getMaxBodySize() {
		return maxBodySize;
	}

	@MCAttribute
	public void setMaxBodySize(int maxBodySize) {
		this.maxBodySize = maxBodySize;
	}

	public BodyCollectingMessageObserver.Strategy getBodyExceedingMaxSizeStrategy() {
		return bodyExceedingMaxSizeStrategy;
	}

	@MCAttribute
	public void setBodyExceedingMaxSizeStrategy(BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy) {
		this.bodyExceedingMaxSizeStrategy = bodyExceedingMaxSizeStrategy;
	}
}
