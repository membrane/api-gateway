package com.predic8.membrane.core.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.TextUtil;

public class ConfigurationFileStore implements ConfigurationStore {

	public Configuration read(String fileName) throws Exception {

		XMLInputFactory factory = XMLInputFactory.newInstance();
		FileInputStream fis = new FileInputStream(fileName);
		XMLStreamReader reader = factory.createXMLStreamReader(fis, Constants.ENCODING_UTF_8);

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
		
		XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(buffer, Constants.ENCODING_UTF_8);
		config.write(writer);
		writer.flush();
		writer.close();

		
		byte[] bArray = buffer.toByteArray();
		OutputStream fos = new FileOutputStream(path);
		buffer.writeTo(fos);
		fos.flush();
		fos.close();
		
		FileWriter out = new FileWriter(path);
		out.write(TextUtil.formatXML(new InputStreamReader(new ByteArrayInputStream(bArray), Constants.ENCODING_UTF_8)));
		out.flush();
		out.close();

	}

}
