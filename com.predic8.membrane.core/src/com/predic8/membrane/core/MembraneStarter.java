/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
