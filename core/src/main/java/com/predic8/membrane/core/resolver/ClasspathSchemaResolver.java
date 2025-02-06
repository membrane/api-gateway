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

import com.google.common.collect.*;
import com.predic8.membrane.core.util.functionalInterfaces.*;

import java.io.*;
import java.util.*;

public class ClasspathSchemaResolver implements SchemaResolver {

	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("classpath");
	}

	@Override
	public InputStream resolve(String url) throws ResourceRetrievalException {
		InputStream is = getClass().getResourceAsStream(normalize(url));
		if (is == null)
			throw new ResourceRetrievalException(url);
		return is;
	}

	@Override
	public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {
		throw new RuntimeException("Not implemented");
	}

	private String normalize(String url) {
		if (url.startsWith("classpath:"))
			url = url.substring(10);
		if (url.startsWith("//"))
			url = url.substring(1);
		return url;
	}

	@Override
	public List<String> getChildren(String url) {
		return null;
	}

	@Override
	public long getTimestamp(String url) {
		return 0;
	}


}
