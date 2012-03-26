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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.servlet.RouterUtil;

/**
 * This embeds Membrane as a servlet.
 */
public class MembraneServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(MembraneServlet.class);

	private Router router;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		try {
			log.debug("loading beans configuration from: " + getContextConfigLocation(config));
			router = RouterUtil.loadRouter(config.getServletContext(), getContextConfigLocation(config));

			log.debug("loading proxies configuration from: " + getProxiesXmlLocation(config));
			router.getConfigurationManager().loadConfiguration(getProxiesXmlLocation(config));

		} catch (Exception e) {
			throw new ServletException(e);
		}
	}
	
	private String getContextConfigLocation(ServletConfig config) {
		return config.getInitParameter("contextConfigLocation");
	}

	private String getProxiesXmlLocation(ServletConfig config) {
		return config.getInitParameter("proxiesXml");
	}

	
	@Override
	public void destroy() {
	}
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		new HttpServletHandler(req, resp, router.getTransport()).run();
	}

}
