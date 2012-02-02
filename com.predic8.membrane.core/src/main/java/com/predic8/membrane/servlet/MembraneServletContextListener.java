package com.predic8.membrane.servlet;

import javax.servlet.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Router;

public class MembraneServletContextListener implements ServletContextListener {

	private static Log log = LogFactory
			.getLog(MembraneServletContextListener.class.getName());

	private Router router;

	public void contextInitialized(ServletContextEvent sce) {
		try {
			log.info("Starting Router...");

			log.debug("loading beans configuration from: "
					+ getContextConfigLocation(sce));
			router = Router.initFromServlet(sce.getServletContext());

			router.setResourceResolver(getResolver(sce));

			log.debug("loading proxies configuration from: "
					+ getProxiesXmlLocation(sce));
			router.getConfigurationManager().loadConfiguration(
					getProxiesXmlLocation(sce));

			log.info("Router running...");
		} catch (Exception ex) {
			log.error("Router not started!", ex);
			throw new RuntimeException("Router not started!", ex);
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		try {
			router.getConfigurationManager().stopHotDeployment();
			router.getTransport().closeAll();
		} catch (Exception ex) {
			log.warn("Failed to shutdown router!", ex);
		}
	}

	private String getContextConfigLocation(ServletContextEvent sce) {
		return sce.getServletContext()
				.getInitParameter("contextConfigLocation");
	}

	private String getProxiesXmlLocation(ServletContextEvent sce) {
		return sce.getServletContext().getInitParameter("proxiesXml");
	}

	private WebAppResolver getResolver(ServletContextEvent sce) {
		WebAppResolver r = new WebAppResolver();
		r.setCtx(sce.getServletContext());
		return r;
	}

}
