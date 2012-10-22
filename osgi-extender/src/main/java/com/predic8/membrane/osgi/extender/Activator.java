/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.osgi.extender;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.concurrent.GuardedBy;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.springframework.osgi.util.OsgiBundleUtils;

import com.predic8.membrane.osgi.extender.logger.Log4JToSLF4J;

public class Activator implements BundleActivator, SynchronousBundleListener {
	private final Object syncRoot = new Object();
	private final ConcurrentHashMap<Long, MembraneApplicationContext> membraneContexts = new ConcurrentHashMap<Long, MembraneApplicationContext>();
	private final Log4JToSLF4J log4jToSLF4JAdapter = new Log4JToSLF4J();
	
	@GuardedBy("syncRoot")
	private boolean stopping = false;

	private long bundleId;

	public void start(BundleContext context) throws Exception {
		org.apache.log4j.Logger.getRootLogger().addAppender(log4jToSLF4JAdapter);
		bundleId = context.getBundle().getBundleId();
		context.addBundleListener(this);
		for (Bundle bundle : context.getBundles())
			if (bundle.getState() == Bundle.ACTIVE)
				handleBundleStart(bundle);
	}

	public void stop(BundleContext context) throws Exception {
		org.apache.log4j.Logger.getRootLogger().removeAppender(log4jToSLF4JAdapter);
		handleSystemStop();
	}

	public void bundleChanged(BundleEvent event) {
		if (event.getBundle().getBundleId() == bundleId)
			return;
		
		switch (event.getType()) {
			case BundleEvent.STARTED: {
				handleBundleStart(event.getBundle());
				break;
			}
			case BundleEvent.STOPPING: {
				if (event.getBundle().getBundleId() == 0)
					handleSystemStop();
				else
					handleBundleStop(event.getBundle());
		
			}
		}
	}
	
	private void handleBundleStart(Bundle bundle) {
		MembraneApplicationContext m =  membraneContexts.get(bundle.getBundleId());
		if (m != null)
			return;
		m = MembraneApplicationContext.create(OsgiBundleUtils.getBundleContext(bundle));
		if (m == null)
			return;
		MembraneApplicationContext m2 = membraneContexts.putIfAbsent(bundle.getBundleId(), m);
		if (m2 != null)
			m = m2;
		m.start();
	}
	
	private void handleBundleStop(Bundle bundle) {
		MembraneApplicationContext m = membraneContexts.remove(bundle.getBundleId());
		if (m == null)
			return;
		m.stop();
	}
	
	private void handleSystemStop() {
		synchronized(syncRoot) {
			if (stopping)
				return;
			else
				stopping = true;
		}
		for (MembraneApplicationContext m : membraneContexts.values())
			m.stop();
		membraneContexts.clear();
	}
}
