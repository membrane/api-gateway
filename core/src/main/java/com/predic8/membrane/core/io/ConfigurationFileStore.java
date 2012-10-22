/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.io;

import java.io.InputStream;

import javax.xml.stream.*;

import com.predic8.membrane.core.*;

public class ConfigurationFileStore implements ConfigurationStore {

	private Router router;

	/**
	 * Reads a configuration from the classpath or a file location
	 */
	public Proxies read(String fileName) throws Exception {
		InputStream is = router.getResourceResolver().resolve(fileName);
		try {
			return read(is);
		} finally {
			is.close();
		}
	}

	private Proxies read(InputStream is) throws Exception {
		XMLStreamReader reader = new FixedStreamReader(XMLInputFactory.newInstance()
				.createXMLStreamReader(is, Constants.UTF_8));

		return (Proxies) new Proxies().parse(reader);
	}

	public void setRouter(Router router) {
		this.router = router;
	}

	public Router getRouter() {
		return router;
	}

}
