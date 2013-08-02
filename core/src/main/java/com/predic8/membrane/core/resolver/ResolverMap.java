/* Copyright 2012-2013 predic8 GmbH, www.predic8.com

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
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.List;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.google.common.base.Objects;
import com.predic8.membrane.core.util.LSInputImpl;
import com.predic8.xml.util.ExternalResolver;

/**
 * A ResolverMap consists of a list of {@link SchemaResolver}s.
 * 
 * It is itself a {@link Resolver}: Requests to resolve a URL are delegated
 * to the corresponding {@link SchemaResolver} child depending on the URL's
 * schema.
 * 
 * Note that this class is not thread-safe! The ResolverMap is setup during
 * Membrane's single-threaded startup and is only used read-only thereafter.
 */
public class ResolverMap implements Cloneable, Resolver {

	public static String combine(String... locations) {
		if (locations.length < 2)
			throw new InvalidParameterException();
		
		if (locations.length > 2) {
			// lfold
			String[] l = new String[locations.length-1];
			System.arraycopy(locations, 0, l, 0, locations.length-1);
			return combine(combine(l), locations[locations.length-1]);
		}
			
		String parent = locations[0];
		String relativeChild = locations[1];
		if (relativeChild.contains(":/") || relativeChild.contains(":\\") || parent == null || parent.length() == 0)
			return relativeChild;
		if (parent.startsWith("file://")) {
			if (relativeChild.startsWith("\\"))
				return "file://" + new File(relativeChild).getAbsolutePath();
			//System.err.println(FileSchemaResolver.normalize(parent));
			File parentFile = new File(FileSchemaResolver.normalize(parent));
			//System.err.println(parentFile.getAbsolutePath());
			if (!parent.endsWith("/") && !parent.endsWith("\\"))
				parentFile = parentFile.getParentFile();
			//System.err.println(parentFile.getAbsolutePath());
			String res = "file://" + new File(parentFile, relativeChild).getAbsolutePath();
			if (relativeChild.endsWith("/") || relativeChild.endsWith("\\"))
					res += "/";
			return res;
		}
		if (parent.contains(":/")) {
			try {
				return new URI(parent).resolve(relativeChild.replaceAll("\\\\", "/")).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else if (parent.startsWith("/")) {
			try {
				return new URI("file:" + parent).resolve(relativeChild).toString().substring(5);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			File parentFile = new File(parent);
			if (!parent.endsWith("/") && !parent.endsWith("\\"))
				parentFile = parentFile.getParentFile();
			return new File(parentFile, relativeChild).getAbsolutePath();
		}
	}

	int count = 0;
	private String[] schemas;
	private SchemaResolver[] resolvers;
	
	public ResolverMap() {
		schemas = new String[10];
		resolvers = new SchemaResolver[10];
		
		// the default config
		addSchemaResolver(new ClasspathSchemaResolver());
		addSchemaResolver(new HTTPSchemaResolver());
		addSchemaResolver(new FileSchemaResolver());
	}
	
	private ResolverMap(ResolverMap other) {
		count = other.count;
		schemas = new String[other.schemas.length];
		resolvers = new SchemaResolver[other.resolvers.length];
		
		System.arraycopy(other.schemas, 0, schemas, 0, count);
		System.arraycopy(other.resolvers, 0, resolvers, 0, count);
	}
	
	@Override
	public ResolverMap clone() {
		return new ResolverMap(this);
	}
	
	public void addSchemaResolver(SchemaResolver sr) {
		for (String schema : sr.getSchemas())
			addSchemaResolver(schema == null ? null : schema + ":", sr);
	}
	
	private void addSchemaResolver(String schema, SchemaResolver resolver) {
		for (int i = 0; i < count; i++)
			if (Objects.equal(schemas[i], schema)) {
				// schema already known: replace resolver
				resolvers[i] = resolver;
				return;
			}
		
		// increase array size
		if (++count > schemas.length) {
			String[] schemas2 = new String[schemas.length * 2];
			System.arraycopy(schemas, 0, schemas2, 0, schemas.length);
			schemas = schemas2;
			SchemaResolver[] resolvers2 = new SchemaResolver[resolvers.length * 2];
			System.arraycopy(resolvers, 0, resolvers2, 0, resolvers.length);
			resolvers = resolvers2;
		}
		
		// determine target index
		int newIndex = count - 1;
		if (newIndex > 0 && schemas[newIndex - 1] == null) {
			// move 'null' resolver to last index
			schemas[newIndex] = schemas[newIndex - 1];
			resolvers[newIndex] = resolvers[newIndex - 1];
			newIndex--;
		}
		
		// insert resolver
		schemas[newIndex] = schema;
		resolvers[newIndex] = resolver;
	}

	private SchemaResolver getSchemaResolver(String uri) {
		for (int i = 0; i < count; i++) {
			if (schemas[i] == null)
				return resolvers[i];
			if (uri.startsWith(schemas[i]))
				return resolvers[i];
		}
		throw new RuntimeException("No SchemaResolver defined for " + uri);
	}
	
	public long getTimestamp(String uri) throws FileNotFoundException {
		return getSchemaResolver(uri).getTimestamp(uri);
	}
	
	public InputStream resolve(String uri) throws ResourceRetrievalException {
		try {
			return getSchemaResolver(uri).resolve(uri);
		} catch (ResourceRetrievalException e) {
			throw e;
		}
	}
	
	public List<String> getChildren(String uri) throws FileNotFoundException {
		return getSchemaResolver(uri).getChildren(uri);
	}

	public HTTPSchemaResolver getHTTPSchemaResolver() {
		return (HTTPSchemaResolver) getSchemaResolver("http:");
	}

	public SchemaResolver getFileSchemaResolver() {
		return getSchemaResolver("file:");
	}
	
	public LSResourceResolver toLSResourceResolver() {
		return new LSResourceResolver() {
			@Override
			public LSInput resolveResource(String type, String namespaceURI,
					String publicId, String systemId, String baseURI) {
				try {
					if (!systemId.contains("://"))
						systemId = new URI(baseURI).resolve(systemId).toString();
					return new LSInputImpl(publicId, systemId, resolve(systemId));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public ExternalResolver toExternalResolver() {
		return new ExternalResolver() {
			@Override
			public InputStream resolveAsFile(String filename, String baseDir) {
				try {
					if(baseDir != null) {
						return ResolverMap.this.resolve(combine(baseDir, filename));
					}
					return ResolverMap.this.resolve(filename);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			protected InputStream resolveViaHttp(Object url) {
				try {
					String url2 = (String) url;
					int q = url2.indexOf('?');
					if (q == -1)
						url2 = url2.replaceAll("/[^/]+/\\.\\./", "/");
					else
						url2 = url2.substring(0, q).replaceAll("/[^/]+/\\.\\./", "/") + url2.substring(q);

					return getSchemaResolver(url2).resolve(url2);
				} catch (ResourceRetrievalException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}
