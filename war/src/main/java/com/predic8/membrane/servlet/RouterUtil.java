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

import java.util.Collection;

import javax.servlet.ServletContext;

import org.springframework.web.context.support.XmlWebApplicationContext;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.servlet.embedded.ServletTransport;

public class RouterUtil {
	public static Router initializeRoutersFromSpringWebContext(XmlWebApplicationContext appCtx, final ServletContext ctx, String configLocation) {
		appCtx.setServletContext(ctx);
		appCtx.setConfigLocation(configLocation);
		appCtx.refresh();

		Collection<Router> routers = appCtx.getBeansOfType(Router.class).values();
		Router theOne = null;
		for (Router r : routers) {
			r.getResolverMap().addSchemaResolver(new FileSchemaWebAppResolver(ctx));
			if (r.getTransport() instanceof ServletTransport) {
				if (theOne != null)
					throw new RuntimeException("Only one <router> may have a <servletTransport> defined.");
				theOne = r;
			}
		}
		
		appCtx.start();
		
		return theOne;
	}

}
