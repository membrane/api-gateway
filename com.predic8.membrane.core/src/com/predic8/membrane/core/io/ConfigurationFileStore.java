package com.predic8.membrane.core.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.util.TextUtil;

public class ConfigurationFileStore implements ConfigurationStore {

	public Configuration read(String fileName) throws Exception {

		XMLInputFactory factory = XMLInputFactory.newInstance();
		FileInputStream fis = new FileInputStream(fileName);
		XMLStreamReader reader = factory.createXMLStreamReader(fis);

		return (Configuration) new Configuration().parse(reader);
	}

	public void write(Configuration config, String path) throws Exception {
		if (config == null)
			throw new IllegalArgumentException(
					"Configuration object to be stored can not be null.");

		if (path == null)
			throw new IllegalArgumentException(
					"File path for saving configuration can not be null.");

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter(buffer);

		config.write(XMLOutputFactory.newInstance().createXMLStreamWriter(
				writer));
		writer.flush();
		writer.close();

		FileWriter out = new FileWriter(path);
		out.write(TextUtil.formatXML(new ByteArrayInputStream(buffer
				.toByteArray())));
		out.flush();
		out.close();

	}

}
