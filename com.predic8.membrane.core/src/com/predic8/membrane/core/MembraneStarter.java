package com.predic8.membrane.core;



import org.eclipse.core.runtime.adaptor.EclipseStarter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

@SuppressWarnings("restriction")
public class MembraneStarter {

	private static String[] bundleUrls = { 
		"file:plugins/org.eclipse.equinox.common_3.5.0.v20090520-1800.jar",
		"file:plugins/org.eclipse.equinox.preferences_3.2.300.v20090520-1800.jar",
		"file:plugins/org.eclipse.equinox.registry_3.4.100.v20090520-1800.jar",
		"file:plugins/org.eclipse.equinox.app_1.2.0.v20090520-1800.jar",
		"file:plugins/org.eclipse.core.jobs_3.4.100.v20090429-1800.jar",
		"file:plugins/org.eclipse.core.contenttype_3.4.0.v20090429-1800.jar",
		"file:plugins/org.eclipse.core.runtime_3.5.0.v20090525.jar",
		"file:plugins/com.predic8.membrane.core_" + Constants.VERSION + ".jar"
	}; 
	
	public static void main(String[] args) throws Exception {
		BundleContext ctx = EclipseStarter.startup(args, null);
		
		for (int i = 0; i < bundleUrls.length; i++) {
			Bundle bundle = ctx.installBundle(bundleUrls[i]);
			bundle.start();
		}
		
		while(true) {
			
		}
	}

}
