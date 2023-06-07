/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.servlet;

import java.io.InputStream;
import java.util.List;

import jakarta.servlet.ServletContext;

import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.resolver.SchemaResolver;

public class FileSchemaWebAppResolver implements SchemaResolver {

	private static final Log log = LogFactory.getLog(FileSchemaWebAppResolver.class.getName());

	private final ServletContext ctx;

	public FileSchemaWebAppResolver(ServletContext ctx) {
		this.ctx = ctx;
	}

	public ServletContext getCtx() {
		return ctx;
	}

	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("file", null);
	}

	@Override
	public InputStream resolve(String url) throws ResourceRetrievalException {
		if (url.startsWith("file:"))
			url = url.substring(5);
		log.debug("loading resource from: " + url);
		InputStream is = ctx.getResourceAsStream(url);
		if (is == null)
			throw new ResourceRetrievalException(url);
		return is;
	}

	@Override
	public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) throws ResourceRetrievalException {
		throw new RuntimeException("Not implemented");
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
