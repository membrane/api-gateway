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
	private int currentSize;
	private boolean newAlgorithm = false;

	/**
	 * EVERY time that exchanges or inflight is changed, modify() MUST be called afterwards
	 */
	private final Queue<AbstractExchange> exchanges = new LinkedList<AbstractExchange>();
	private Map<AbstractExchange, Request> inflight = new ConcurrentHashMap<AbstractExchange, Request>();

	private long lastModification = System.currentTimeMillis();

	public void snap(final AbstractExchange exc, final Flow flow) {
		if(newAlgorithm) {
			newSnap(exc, flow);
		}else
			oldSnap(exc, flow);
	}

	private void newSnap(AbstractExchange exc, Flow flow) {
		AbstractExchange excCopy = null;
		try {
            if (flow == Flow.REQUEST) {
				excCopy = cleanSnapshot(exc.createSnapshot());
				snapInternal(excCopy, flow);
			}
            else
                excCopy = cleanSnapshot(Exchange.updateCopy(exc, getExchangeById((int) exc.getId())));
			addObservers(exc, excCopy, flow);
			modify();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

	}

	private void addObservers(AbstractExchange exc, AbstractExchange excCopy, Flow flow) throws Exception {
		Message msg = null;
		if(flow == Flow.REQUEST) {
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
                    cleanSnapshot(Exchange.updateCopy(exc,excCopy));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
			@Override
			public void setExchangeFinished() {
				try {
                    cleanSnapshot(Exchange.updateCopy(exc,excCopy));

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
        cleanSnapshot(Exchange.updateCopy(exc,excCopy));
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

	private void oldSnap(AbstractExchange exc, Flow flow) {
		// TODO: [fix me] support multi-snap
		// TODO: [fix me] snap message headers and request *here*, not in observer/response
		exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
			@Override
			public void setExchangeFinished() {
				inflight.remove(exc);
			}
		});

		if (flow == Flow.REQUEST) {
			exc.getRequest().addObserver(
					new MessageObserver() {
						@Override
						public void bodyRequested(AbstractBody body) {
						}
						@Override
						public void bodyComplete(AbstractBody body) {
							Response r = exc.getResponse();
							if (r != null) {
								AbstractBody b = r.getBody();
								if (b != null && b.isRead())
									return; // request-bodyComplete might occur after response-bodyComplete
							}
							//System.out.println("Exchange put inflight " + exc.hashCode() + " " + exc.getRequest().getStartLine());
							inflight.put(exc, exc.getRequest());
							modify();
						}
					}
					);
			return;
		}

		try {
			Message m = exc.getResponse();
			if (m != null)
				m.addObserver(new MessageObserver() {
					public void bodyRequested(AbstractBody body) {
					}
					public void bodyComplete(AbstractBody body) {
						snapInternal(exc, flow);
						inflight.remove(exc);
						modify();
						//System.out.println("Exchange remove inflight " + exc.hashCode());
					}
				});
			else {
				inflight.remove(exc);
				modify();
				//System.out.println("Exchange remove inflight " + exc.hashCode() + " (2)");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private synchronized void snapInternal(AbstractExchange exc, Flow flow) {
		if (exc.getHeapSizeEstimation() > maxSize)
			return;

		makeSpaceIfNeeded(exc);

		exchanges.offer(exc);
		modify();
		currentSize += exc.getHeapSizeEstimation();
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
		for(AbstractExchange exc : exchanges) {
			if (exc.getRule().equals(key)) {
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

		for (Map.Entry<AbstractExchange, Request> entry : inflight.entrySet()) {
			AbstractExchange ex = entry.getKey();
			Request req = entry.getValue();
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
	public synchronized AbstractExchange getExchangeById(int id) {
		for (AbstractExchange exc : getAllExchangesAsList()) {
			if (exc.getId() == id) {
				return exc;
			}
		}
		for (AbstractExchange exc : inflight.keySet())
			if (exc.getId() == id) {
				return exc;
			}
		return null;
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

	private void makeSpaceIfNeeded(AbstractExchange exc) {
		while (!hasEnoughSpace(exc)) {
			currentSize -= exchanges.poll().getHeapSizeEstimation();
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

	public boolean isNewAlgorithm() {
		return newAlgorithm;
	}

	@MCAttribute
	public void setNewAlgorithm(boolean newAlgorithm) {
		this.newAlgorithm = newAlgorithm;
	}
}
