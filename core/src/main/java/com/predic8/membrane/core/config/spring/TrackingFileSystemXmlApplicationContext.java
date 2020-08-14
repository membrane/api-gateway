/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config.spring;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionStoreException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.xml.sax.SAXParseException;

/**
 * Delegates everything to {@link FileSystemXmlApplicationContext}.
 *
 * Additionally adds aspects of {@link TrackingApplicationContext}, {@link BaseLocationApplicationContext} and
 * {@link CheckableBeanFactory}.
 */
public class TrackingFileSystemXmlApplicationContext extends FileSystemXmlApplicationContext implements
TrackingApplicationContext, BaseLocationApplicationContext, CheckableBeanFactory {
	private static final Logger log = LoggerFactory.getLogger(TrackingFileSystemXmlApplicationContext.class.getName());

	private List<File> files = new ArrayList<File>();

	public TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh) throws BeansException {
		super(configLocations, refresh);
	}

	public TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh, ApplicationContext parent) throws BeansException {
		super(configLocations, refresh, parent);
	}

	@Override
	public Resource getResource(String location) {
		final Resource r = super.getResource(location);
		try {
			files.add(r.getFile());
		} catch (IOException e) {
			log.debug("",e);
		}
		return new Resource() {
			Resource r2 = r;

			public boolean exists() {
				return r2.exists();
			}

			public InputStream getInputStream() throws IOException {
				return r2.getInputStream();
			}

			public boolean isReadable() {
				return r2.isReadable();
			}

			public boolean isOpen() {
				return r2.isOpen();
			}

			public URL getURL() throws IOException {
				return r2.getURL();
			}

			public URI getURI() throws IOException {
				return r2.getURI();
			}

			public File getFile() throws IOException {
				return r2.getFile();
			}

			public long lastModified() throws IOException {
				return r2.lastModified();
			}

			public Resource createRelative(String relativePath) throws IOException {
				Resource r = r2.createRelative(relativePath);
				files.add(r.getFile());
				return r;
			}

			public String getFilename() {
				return r2.getFilename();
			}

			public String getDescription() {
				return r2.getDescription();
			}

			public long contentLength() throws IOException {
				return r2.contentLength();
			}

			@Override
			public String toString() {
				return r2.toString();
			}
		};
	}

	public List<File> getFiles() {
		return files;
	}

	public String getBaseLocation() {
		return getConfigLocations()[0];
	}

	@Override
	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		files.clear();
		super.loadBeanDefinitions(beanFactory);
	}

	@Override
	public void checkForInvalidBeanDefinitions() throws InvalidConfigurationException {
		try {
			DefaultListableBeanFactory beanFactory = createBeanFactory();
			beanFactory.setSerializationId(null);
			customizeBeanFactory(beanFactory);
			loadBeanDefinitions(beanFactory);
		} catch (XmlBeanDefinitionStoreException e) {
			handleXmlBeanDefinitionStoreException(e);
		} catch (IOException ex) {
			throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}

	public static void handleXmlBeanDefinitionStoreException(XmlBeanDefinitionStoreException e) throws InvalidConfigurationException {
		Throwable cause = e.getCause();
		if (cause != null) {
			if (cause instanceof SAXParseException) {
				int line = ((SAXParseException) cause).getLineNumber();

				throw new InvalidConfigurationException("line " + line + ": " + cause.getMessage());
			}
		}
		throw e;
	}

	@Override
	public String toString() {
		return "Membrane Service Proxy's Spring Context";
	}
}