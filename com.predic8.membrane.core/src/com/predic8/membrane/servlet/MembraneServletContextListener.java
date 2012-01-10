package com.predic8.membrane.servlet;

import javax.servlet.*;

import com.predic8.membrane.core.HotDeploymentThread;
import com.predic8.membrane.core.Router;

public class MembraneServletContextListener implements ServletContextListener {

	Router router;
	HotDeploymentThread hdt;

	public void contextInitialized(ServletContextEvent sce) {
		try {
			router = Router.init(getConfPath(sce, "monitorBeansXml"));

			router.setResourceResolver(getResolver(sce));
			String proxiesXml = sce.getServletContext().getInitParameter("proxiesXml");
			router.getConfigurationManager().loadConfiguration(proxiesXml);
			
			hdt = new HotDeploymentThread(router);
			hdt.setProxiesFile(sce.getServletContext().getRealPath(proxiesXml));
			hdt.start();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		try {
			hdt.interrupt();
			hdt.join();
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
