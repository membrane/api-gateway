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
