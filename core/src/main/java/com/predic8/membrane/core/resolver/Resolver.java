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
package com.predic8.membrane.core.resolver;

import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public interface Resolver {

	/**
	 * Returns the InputStream for the requested URL.
	 * @throws ResourceRetrievalException if the resource identified by the URL does not exist.
	 */
	public InputStream resolve(String url) throws ResourceRetrievalException;

	/**
	 * Calls the consumer when the InputStream for the requested URL changes.
	 * @throws ResourceRetrievalException if the resource identified by the URL does not exist.
	 */
	public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) throws ResourceRetrievalException;

	/**
	 * Returns the list of child resources of the resource identified by the URL.
	 * <p>
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
