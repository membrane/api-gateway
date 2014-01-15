/* Copyright 2014 predic8 GmbH, www.predic8.com

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

import java.util.Properties;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.annot.NamespaceUtil;

/**
 * Publishes the {@link NamespaceHandler} service, which handles custom
 * Blueprint XML elements from the Membrane namespaces (the ones defined using
 * {@link MCMain} etc.).
 */
public class Activator implements BundleActivator {
	
	public static BundleContext context;

	private ServiceReference logServiceReference;
	private ServiceRegistration registerService;

	public void start(BundleContext arg0) throws Exception {
		context = arg0;
		
		logServiceReference = arg0.getServiceReference(LogService.class.getName());
		OsgiAppender.setLogService((LogService) arg0.getService(logServiceReference));
		
		Properties p = new Properties();
		p.put("osgi.service.blueprint.namespace", new NamespaceUtil().getTargetNamespaces().toArray(new String[0]));
		
		registerService = arg0.registerService(org.apache.aries.blueprint.NamespaceHandler.class.getName(), 
				new NamespaceHandler(), p);
	}

	public void stop(BundleContext arg0) throws Exception {
		registerService.unregister();
		OsgiAppender.setLogService(null);
		arg0.ungetService(logServiceReference);
	}

}
