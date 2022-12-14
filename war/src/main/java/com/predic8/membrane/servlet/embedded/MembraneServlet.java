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

package com.predic8.membrane.servlet.embedded;

import java.io.IOException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.support.XmlWebApplicationContext;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.servlet.RouterUtil;
import com.predic8.membrane.servlet.config.spring.BaseLocationXmlWebApplicationContext;

/**
 * This embeds Membrane as a servlet.
 */
public class MembraneServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(MembraneServlet.class);

	private XmlWebApplicationContext appCtx;
	private Router router;

	@Override
	public void init(ServletConfig config) throws ServletException {
		try {
			appCtx = new BaseLocationXmlWebApplicationContext();

			log.debug("loading beans configuration from: " + getProxiesXmlLocation(config));
			router = RouterUtil.initializeRoutersFromSpringWebContext(appCtx, config.getServletContext(), getProxiesXmlLocation(config));
			if (router == null)
				throw new RuntimeException("No <router> with a <servletTransport> was found. To use <router> with <transport>, use MembraneServletContextListener instead of MembraneServlet.");

		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	@Override
	public void destroy() {
		appCtx.stop();
	}

	private String getProxiesXmlLocation(ServletConfig config) {
		return config.getInitParameter("proxiesXml");
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		new HttpServletHandler(req, resp, router.getTransport()).run();
	}

}
