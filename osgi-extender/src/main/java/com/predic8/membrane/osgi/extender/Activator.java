
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
