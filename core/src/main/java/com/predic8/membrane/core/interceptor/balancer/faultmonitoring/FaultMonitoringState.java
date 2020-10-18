/* Copyright 2015 Fabian Kessler, Optimaize

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer.faultmonitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Contains the fault state of the nodes.
 *
 * <p>This is used by the {@link FaultMonitoringStrategy}.</p>
 *
 * <p>It is kept separate to survive context restarts, which happen when config changes are detected and auto-reloaded.</p>
 *
 * <p>It is a singleton to ensure the same data is used.</p>
 *
 * @author Fabian Kessler / Optimaize
 */
class FaultMonitoringState {

	private static Log log = LogFactory.getLog(FaultMonitoringState.class.getName());


	/**
	 */
	private static FaultMonitoringState INSTANCE = new FaultMonitoringState();
	public static FaultMonitoringState getInstance() {
		return INSTANCE;
	}


	/**
	 * Key = destination "host:port"
	 */
	private final ConcurrentMap<String, NodeFaultProfile> map = new ConcurrentHashMap<String, NodeFaultProfile>();

	private Timer timer;

	private FaultMonitoringState() {
	}


	public ConcurrentMap<String, NodeFaultProfile> getMap() {
		return map;
	}


	/**
	 * Call this to activate the background maintenance task that removes outdated profiles by time.
	 * On successive calls, it first removes the previous timer, and then creates a new one with the given times.
	 */
	public synchronized void scheduleRemoval(final long clearFaultyProfilesByTimerAfterLastFailureSeconds, long clearFaultyTimerIntervalSeconds) {
		if (timer!=null) {
			//first call
			timer.cancel();
		}

		timer = new Timer(true); //using a daemon thread
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					Iterator<NodeFaultProfile> iterator = map.values().iterator();
					while (iterator.hasNext()) {
						NodeFaultProfile current = iterator.next();
						long lastFailureTimestamp = current.getLastFailureTimestamp();
						long diff = System.currentTimeMillis() - lastFailureTimestamp;
						if (diff >= clearFaultyProfilesByTimerAfterLastFailureSeconds) {
							//it has been a while, clear it.
							//we could ask some pre-removal callback for checking if the service looks good now.
							iterator.remove(); //this concurrent modification is safe because we do it on an Iterator object.
						}
					}
				} catch (Exception e) {
					//must catch all exceptions, otherwise the timer task aborts and is not run anymore.
					log.error("Unexpected exception when running maintenance timer", e);
				}
			}
		}, clearFaultyTimerIntervalSeconds, clearFaultyTimerIntervalSeconds);
	}
}
