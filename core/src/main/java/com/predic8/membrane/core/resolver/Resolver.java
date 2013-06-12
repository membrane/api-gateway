package com.predic8.membrane.core.resolver;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public interface Resolver {
	
	/**
	 * Returns the InputStream for the requested URL.
	 * @throws FileNotFoundException if the resource identified by the URL does not exist.
	 */
	public InputStream resolve(String url) throws FileNotFoundException;
	
	/**
	 * Returns the list of child resources of the resource identified by the URL.
	 * 
	 * For example, a list of filenames in the directory identified by the URL.
	 * 
	 * @throws FileNotFoundException if the resource identified by the URL does not exist.
	 * @return null if the resolver does not support this functionality.
	 */
	public List<String> getChildren(String url) throws FileNotFoundException;
	
	/**
	 * Returns the modification date of the resource.
	 * 
	 * @throws FileNotFoundException if the resource identified by the URL does not exist.
	 * @return 0 if the resolver does not support this functionality.
	 */
	public long getTimestamp(String url) throws FileNotFoundException;
}
