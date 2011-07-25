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
package com.predic8.membrane.core.io;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;

public class ConfigurationFileStore implements ConfigurationStore {

	private Router router;

	/**
	 * Reads a configuration from the classpath or a file location
	 * 
	 * @param fileName
	 *            Path to rules.xml. Use classpath:<path> to load from the
	 *            classpath.
	 */
	public Configuration read(String fileName) throws Exception {

		if (fileName.startsWith("classpath:"))
			return read(getClass().getResourceAsStream(fileName.substring(10)));
		else
			return read(new FileInputStream(fileName));

	}

	private Configuration read(InputStream is) throws XMLStreamException {
		XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is, Constants.UTF_8);

		return (Configuration) new Configuration(router).parse(reader);
	}

	public void setRouter(Router router) {
		this.router = router;
	}

}
