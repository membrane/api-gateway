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

package com.predic8.membrane.osgi.extender;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.osgi.extender.classloading.BundleClassLoader;
import com.predic8.membrane.osgi.extender.classloading.OverlayClassLoader;

/**
 * Manages the life-cycle of the {@link Router} for OSGi bundles containing proxies.xml files.
 */
public class MembraneApplicationContext {
	
	private static final Logger logger = LoggerFactory.getLogger(MembraneApplicationContext.class);

	private static final String CONTEXT_DIR = "/META-INF/membrane/";
	private static final String CONTEXT_FILES = "*.xml";
	
	private static final ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * Creates a new MembraneApplicationContext for the specified bundle.
	 * @param context the bundle's local context
	 * @return null, if the bundle is not a membrane bundle
	 */
	public static MembraneApplicationContext create(BundleContext context) {
		Enumeration<?> configs = context.getBundle().findEntries(CONTEXT_DIR, CONTEXT_FILES, false);
		if (configs == null || !configs.hasMoreElements())
			return null;
		while (configs.hasMoreElements()) {
			URL url = (URL)configs.nextElement();
			try {
			InputStream is = url.openStream();
			try {
				Document doc = parse(is);
				String name = doc.getDocumentElement().getLocalName();
				String uri  = doc.getDocumentElement().getNamespaceURI();
				
				if ("proxies".equals(name) && "http://membrane-soa.org/schemas/proxies/v1/".equals(uri))
					return new MembraneApplicationContext(context, url.toString());
			} finally {
				is.close();
			}
			} catch (IOException e) {
				logger.error("while attempting to parse proxies.xml", e);
			}
		}
		return null;
	}
	
	private final BundleContext context;
	private final String proxiesConfigurationURL;
	
	private Future<Router> initializingRouter;
	
	private MembraneApplicationContext(BundleContext context, String proxiesConfigurationURL) {
		this.context = context;
		this.proxiesConfigurationURL = proxiesConfigurationURL;
	}
	
	public synchronized void start() {
		if (initializingRouter != null)
			return;
		
		initializingRouter = executor.submit(new Callable<Router>() {
			public Router call() throws Exception {
				try {
					String monitorBeansResource;
					ClassLoader classLoader;

					if (hasMonitorBeans(context.getBundle())) {
						logger.warn("using provided monitor-beans.xml");
						monitorBeansResource = "classpath:/META-INF/membrane/monitor-beans.xml";
						classLoader = new OverlayClassLoader(new BundleClassLoader(context.getBundle()), getClass().getClassLoader());
					} else {
						logger.warn("using default monitor-beans.xml");
						monitorBeansResource = "classpath:/com/predic8/membrane/osgi/extender/monitor-beans.xml";
						classLoader = getClass().getClassLoader();
					}
					
					Router router = Router.init(monitorBeansResource, classLoader);
					router.setResourceResolver(new BundleResolver());
					router.getConfigurationManager().loadConfiguration(proxiesConfigurationURL);
					return router;
				} catch (Exception e) {
					logger.error("loading proxies configuratio", e);
					throw e;
				}
			}

			private boolean hasMonitorBeans(Bundle bundle) {
				Enumeration<?> e = bundle.findEntries(CONTEXT_DIR, "monitor-beans.xml", false);
				if (e == null)
					return false;
				return e.hasMoreElements();
			}
		});
	}
	


	public synchronized void stop() {
		if (initializingRouter == null)
			return;
		final Future<Router> initializingRouter2 = initializingRouter; 
		initializingRouter = null;
		executor.execute(new Runnable() {
			public void run() {
				try {
					initializingRouter2.get().getTransport().closeAll();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					// was already logged
				} catch (IOException e) {
					logger.error("stopping router", e);
				}
			}
		});
	}
	
	
    private static DocumentBuilderFactory dbf;

    private static Document parse(InputStream is) throws IOException {
    	try {
    		if (dbf == null) {
    			dbf = DocumentBuilderFactory.newInstance();
    			dbf.setNamespaceAware(true);
    		}
    		DocumentBuilder db = dbf.newDocumentBuilder();
    		db.setErrorHandler(new ErrorHandler() {
    			public void warning(SAXParseException exception) throws SAXException {
    			}
    			public void error(SAXParseException exception) throws SAXException {
    			}
    			public void fatalError(SAXParseException exception) throws SAXException {
    				throw exception;
    			}
    		});
    		return db.parse(is);
    	} catch (Exception e) {
    		throw new IOException(e);
    	}
    }


}
