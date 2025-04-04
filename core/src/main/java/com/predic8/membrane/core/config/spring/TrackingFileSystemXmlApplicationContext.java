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

import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;
import org.xml.sax.*;

import java.io.*;
import java.net.URI;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static java.util.Arrays.stream;

/**
 * Delegates everything to {@link FileSystemXmlApplicationContext}.
 * <p>
 * Additionally, adds aspects of {@link TrackingApplicationContext}, {@link BaseLocationApplicationContext} and
 * {@link CheckableBeanFactory}.
 */
public class TrackingFileSystemXmlApplicationContext extends FileSystemXmlApplicationContext implements
TrackingApplicationContext, BaseLocationApplicationContext, CheckableBeanFactory {
	private static final Logger log = LoggerFactory.getLogger(TrackingFileSystemXmlApplicationContext.class.getName());

	private final List<File> files = new ArrayList<>();

	public TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh) throws BeansException {
		super(convertFileURIsToPaths(configLocations), refresh);
	}

	public TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh, ApplicationContext parent) throws BeansException {
		super(convertFileURIsToPaths(configLocations), refresh, parent);
	}

	private static String[] convertFileURIsToPaths(String[] uris) {
		return stream(uris).map(URIUtil::pathFromFileURI).toArray(String[]::new);
	}

	@Override
	public Resource getResource(String location) {

		Resource r = string2Resource(location);

        try {
            files.add(r.getFile());
        } catch (IOException e) {
			log.error("Cloud not init from {}.", e);
            throw new RuntimeException("Cloud not init from %s".formatted(location) ,e);
        }

        return new Resource() {
			final Resource r2 = r;

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

	/**
	 *
	 * @param location String like /foo, file:/foo, classpath:/foo
	 * @return Resource Spring Resource
	 */
	private @NotNull Resource string2Resource(String location) {
		if (location.startsWith("/")) {
			return new FileSystemResource(location);
		}
		if (location.startsWith("classpath:")) {
			return new ClassPathResource(location.substring("classpath:".length()));
		}
		if (location.startsWith("file:")) {
            try {
                return new FileUrlResource(location);
            } catch (MalformedURLException e) {
				log.error("Failed to resolve file {}. Trying something else.",location, e);
            }
        }
		if (location.startsWith("file:") || location.startsWith("http:") || location.startsWith("https:")) {
            try {
                return new UrlResource(location);
            } catch (MalformedURLException e) {
				log.error("Failed to resolve file {}. Trying something else.",location, e);
            }
        }
		return super.getResource(location);
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
			if (cause instanceof SAXParseException saxpe) {
				int line = saxpe.getLineNumber();

				throw new InvalidConfigurationException(e.getResourceDescription() + " line " + line + ": " + concatMessageAndCauseMessages(cause));
			}
		}
		throw e;
	}

	@Override
	public String toString() {
		return "Membrane Service Proxy's Spring Context";
	}
}