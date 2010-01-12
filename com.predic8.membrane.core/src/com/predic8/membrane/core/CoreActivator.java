package com.predic8.membrane.core;

import java.io.File;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.springframework.core.io.UrlResource;

/**
 * The activator class controls the plug-in life cycle
 */
public class CoreActivator extends Plugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.predic8.membrane.core";

	// The shared instance
	private static CoreActivator plugin;

	/**
	 * The constructor
	 */
	public CoreActivator() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
		URL bundleRootURL = CoreActivator.getDefault().getBundle().getEntry("/");
		URL url = FileLocator.resolve(bundleRootURL);
				
		File file = new File(url.getPath());
		String projectRoot = file.getParentFile().getParentFile().getPath();
		
		String fileName = projectRoot + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml";
			
		if (new File(fileName).exists()) {
			Router.init(new UrlResource(fileName));
		} else {
			Router.init("configuration/monitor-beans.xml");
		}
		
		try {
			Router.getInstance().getConfigurationManager().loadConfiguration(System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml");
		} catch (Exception e) {
			//we ignore this exception because the monitor can start up without loading a rules configuration
			//we need to throw an exception because the router must display an error message
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static CoreActivator getDefault() {
		return plugin;
	}

}
