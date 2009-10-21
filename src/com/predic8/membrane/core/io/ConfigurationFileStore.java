package com.predic8.membrane.core.io;

import java.io.FileInputStream;
import java.io.FileWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;


public class ConfigurationFileStore implements ConfigurationStore {
	
	public Configuration read(String fileName) throws Exception {
		
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    FileInputStream fis = new FileInputStream(fileName);
	    XMLStreamReader reader = factory.createXMLStreamReader(fis);
	    
	    return (Configuration)new Configuration().parse(reader);
	}

	public void write(Configuration config, String path) throws Exception{
		if (config == null) 
			throw new IllegalArgumentException("Configuration object to be stored can not be null.");
		
		if (path == null) 
			throw new IllegalArgumentException("File path for saving configuration can not be null.");
		
		XMLOutputFactory factory = XMLOutputFactory.newInstance();
	    XMLStreamWriter writer = factory.createXMLStreamWriter(new FileWriter(path));
		config.write(writer);
	}

}
