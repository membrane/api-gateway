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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

public class FileSchemaResolver implements SchemaResolver {
	
	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("file", null);
	}
	
	public InputStream resolve(String url) throws FileNotFoundException {
	    return new FileInputStream(new File(normalize(url)));
	}
	
	private String normalize(String uri) {
	    if(uri.startsWith("file:")) {
	    	try {
	    		uri = new URL(uri).getPath();
	    	} catch (Exception e) {
	    		throw new RuntimeException(e);
	    	}
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

		return null;
	}

	@Override
	public long getTimestamp(String url) {
		return new File(normalize(url)).lastModified();
	}
}
