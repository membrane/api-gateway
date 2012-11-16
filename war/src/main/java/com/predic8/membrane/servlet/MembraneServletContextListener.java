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

package com.predic8.membrane.servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;

public class MembraneServletContextListener implements ServletContextListener {

	private static Log log = LogFactory.getLog(MembraneServletContextListener.class);

	private Router router;

	public void contextInitialized(ServletContextEvent sce) {
		try {
			log.info(Constants.PRODUCT_NAME + " starting...");

			log.debug("loading beans configuration from: " + getContextConfigLocation(sce));
			router = RouterUtil.loadRouter(sce.getServletContext(), getContextConfigLocation(sce));

			log.debug("loading proxies configuration from: " + getProxiesXmlLocation(sce));
			router.getConfigurationManager().loadConfiguration(getProxiesXmlLocation(sce));

			log.info(Constants.PRODUCT_NAME + " running.");
		} catch (Exception ex) {
			log.error("Router not started!", ex);
			throw new RuntimeException("Router not started!", ex);
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		try {
			router.getConfigurationManager().stopHotDeployment();
			router.shutdown();
		} catch (Exception ex) {
			log.warn("Failed to shutdown router!", ex);
		}
	}

	private String getContextConfigLocation(ServletContextEvent sce) {
		return sce.getServletContext().getInitParameter("contextConfigLocation");
	}

	private String getProxiesXmlLocation(ServletContextEvent sce) {
		return sce.getServletContext().getInitParameter("proxiesXml");
	}
}
