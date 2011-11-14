package com.predic8.membrane.servlet;

import javax.servlet.*;

import com.predic8.membrane.core.Router;

public class MembraneServletContextListener implements ServletContextListener {

	Router router;

	public void contextInitialized(ServletContextEvent sce) {
		try {
			router = Router.init(getConfPath(sce, "monitorBeansXml"));

			router.setResourceResolver(getResolver(sce));
			router.getConfigurationManager().loadConfiguration(
					sce.getServletContext().getInitParameter("proxiesXml"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		try {
			router.getTransport().closeAll();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private WebAppResolver getResolver(ServletContextEvent sce) {
		WebAppResolver r = new WebAppResolver();
		r.setAppBase(sce.getServletContext().getRealPath("/"));
		return r;
	}

	private String getConfPath(ServletContextEvent sce, String param) {
		return sce.getServletContext().getRealPath("/")
				+ sce.getServletContext().getInitParameter(param);
	}

}
