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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;
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
		return Lists.newArrayList((String)null);
	}

	@Override
	public InputStream resolve(String url) throws FileNotFoundException {
		log.debug("loading resource from: " + url);
		return ctx.getResourceAsStream(url);
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
