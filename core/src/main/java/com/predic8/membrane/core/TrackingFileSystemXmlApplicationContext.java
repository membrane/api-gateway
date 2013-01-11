package com.predic8.membrane.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Delegates everything to {@link FileSystemXmlApplicationContext}. Additionally builds
 * a list of the files opened during the context refresh.
 */
class TrackingFileSystemXmlApplicationContext extends FileSystemXmlApplicationContext {
	private static final Log log = LogFactory.getLog(TrackingFileSystemXmlApplicationContext.class.getName());
	
	private List<File> files = new ArrayList<File>();
	
	TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh) throws BeansException {
		super(configLocations, refresh);
	}

	TrackingFileSystemXmlApplicationContext(String[] configLocations, boolean refresh, ApplicationContext parent) throws BeansException {
		super(configLocations, refresh, parent);
	}

	@Override
	public void refresh() throws BeansException, IllegalStateException {
		files.clear();
		super.refresh();
	}
	
	public Resource getResource(String location) {
		final Resource r = super.getResource(location);
		try {
			files.add(r.getFile());
		} catch (IOException e) {
			log.debug(e);
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
		};
	}
	
	public List<File> getFiles() {
		return files;
	}
}