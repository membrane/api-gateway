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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

public class FileSchemaResolver implements SchemaResolver {

	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("file", null);
	}

	public InputStream resolve(String url) throws ResourceRetrievalException {
		try {
			return new FileInputStream(new File(normalize(url)));
		} catch (FileNotFoundException e) {
			throw new ResourceRetrievalException(url, e);
		}
	}

	public static String normalize(String uri) {
		if(uri.startsWith("file:///")) {
			if (uri.length() > 9 && uri.charAt(9) == '/')
				uri = uri.charAt(8) + ":\\" + uri.substring(9);
			else
				uri = "/" + uri.substring(8);
		}
		if(uri.startsWith("file://")) {
			if (uri.length() > 8 && uri.charAt(8) == '/')
				uri = uri.charAt(7) + ":\\" + uri.substring(9);
			else
				uri = "/" + uri.substring(7);
		}
		if(uri.startsWith("file:")) {
			uri = uri.substring(5);
		}
		return uri;
	}

	@Override
	public List<String> getChildren(String url) {
		String[] children = new File(normalize(url)).list();
		if (children == null)
			return null;
		ArrayList<String> res = new ArrayList<String>(children.length);
		for (String child : children)
			res.add(child);
		return res;
	}

	@Override
	public long getTimestamp(String url) {
		return new File(normalize(url)).lastModified();
	}
}
