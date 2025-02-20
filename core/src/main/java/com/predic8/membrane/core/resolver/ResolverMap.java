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

import com.google.common.base.Objects;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.kubernetes.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.functionalInterfaces.*;
import com.predic8.xml.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.w3c.dom.ls.*;

import java.io.*;
import java.net.*;
import java.net.URI;
import java.security.*;
import java.util.*;

import static com.predic8.membrane.core.util.URIUtil.*;

/**
 * A ResolverMap consists of a list of {@link SchemaResolver}s.
 * <p>
 * It is itself a {@link Resolver}: Requests to resolve a URL are delegated
 * to the corresponding {@link SchemaResolver} child depending on the URL's
 * schema.
 * <p>
 * Note that this class is not thread-safe! The ResolverMap is setup during
 * Membrane's single-threaded startup and is only used read-only thereafter.
 */
@MCElement(name = "resolverMap")
public class ResolverMap implements Cloneable, Resolver {

    private static final Logger log = LoggerFactory.getLogger(ResolverMap.class.getName());

    /**
     * First param is the parent. The following params will be combined to one path
     * e.g. "/foo/bar", "baz/x.yaml" ", "soo" => "/foo/bar/baz/soo"
     * See ResolverMapCombinedTest
     *
     * @param locations List of relative paths
     * @return combined path
     */
    public static String combine(String... locations) {
        String resolved = combineInternal(locations);
        log.debug("Resolved locations: {} to: {}", locations, resolved);
        return resolved;
    }

    private static String combineInternal(String... locations) {
        if (locations.length < 2)
            throw new InvalidParameterException();

        if (locations.length > 2) {
            // lfold
            String[] l = new String[locations.length - 1];
            System.arraycopy(locations, 0, l, 0, locations.length - 1);
            return combine(combine(l), locations[locations.length - 1]);
        }

        String parent = locations[0];
        String relativeChild = locations[1];

        if (relativeChild.contains(":/") || relativeChild.contains(":\\") || parent == null || parent.isEmpty())
            return relativeChild;
        if (parent.startsWith("file://")) {
            if (relativeChild.startsWith("\\") || relativeChild.startsWith("/")) {
                return  convertPath2FilePathString( new File(relativeChild).getAbsolutePath());
            }
            File parentFile = new File(pathFromFileURI(parent));
            if (!parent.endsWith("/") && !parent.endsWith("\\"))
                parentFile = parentFile.getParentFile();
            try {
                return keepTrailingSlash(parentFile, relativeChild);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Error combining: " + Arrays.toString(locations), e);
            }
        }
        if (parent.contains(":/")) {
            try {
                if (parent.startsWith("http"))
                    return new URI(parent).resolve(prepare4Uri(relativeChild)).toString();
                if (parent.startsWith("classpath:"))
                    return new URI(parent).resolve(prepare4Uri(relativeChild)).toString();
                return convertPath2FileURI(parent).resolve(prepare4Uri(relativeChild)).toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (parent.startsWith("/")) {
            try {
                return pathFromFileURI(convertPath2FileURI(parent).resolve(relativeChild));
            } catch (Exception e) {
                throw new ConfigurationException("""
                        Error resolving paths to resources. Check proxies.xml, OpenAPI, JSON/XSD Schema and WSDL documents for references.
                        
                        Parent path: %s
                        Child path: %s
                        """.formatted(parent, relativeChild));
            }
        }
        File parentFile = new File(parent);
        if (!parent.endsWith("/") && !parent.endsWith("\\"))
            parentFile = parentFile.getParentFile();
        try {
            return new File(parentFile, relativeChild).getCanonicalPath();
        } catch (IOException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    /**
     * Prepares a path string to be used to construct a URI
     *
     * @param path
     * @return
     */
    protected static String prepare4Uri(String path) {
        path = path.replaceAll("\\\\", "/");
        path = path.replaceAll(" ", "%20");
        return path;
    }

    protected static @NotNull String keepTrailingSlash(File parentFile, String relativeChild) throws URISyntaxException {
        String res = toFileURIString(new File(parentFile, relativeChild));
        if (endsWithSlash(relativeChild))
            return res + "/";
        return res;
    }

    private static boolean endsWithSlash(String path) {
        return path.endsWith("/") || path.endsWith("\\");
    }

    int count = 0;
    private String[] schemas;
    private SchemaResolver[] resolvers;

    public ResolverMap() {
        this(null, null);
    }

    public ResolverMap(HttpClientFactory httpClientFactory, KubernetesClientFactory kubernetesClientFactory) {
        schemas = new String[10];
        resolvers = new SchemaResolver[10];

        // the default config
        addSchemaResolver(new ClasspathSchemaResolver());
        addSchemaResolver(new HTTPSchemaResolver(httpClientFactory));
        addSchemaResolver(new KubernetesSchemaResolver(kubernetesClientFactory));
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

    public void addRuleResolver(Router r) {
        addSchemaResolver(new RuleResolver(r));
    }

    public long getTimestamp(String uri) throws FileNotFoundException {
        return getSchemaResolver(uri).getTimestamp(uri);
    }

    public InputStream resolve(String uri) throws ResourceRetrievalException {
        return getSchemaResolver(uri).resolve(uri);
    }

    @Override
    public void observeChange(String uri, ExceptionThrowingConsumer<InputStream> consumer) throws ResourceRetrievalException {
        getSchemaResolver(uri).observeChange(uri, consumer);
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
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            if (systemId == null)
                return null;
            try {
                if (!systemId.contains("://"))
                    systemId = new URI(baseURI).resolve(systemId).toString();
                return new LSInputImpl(publicId, systemId, resolve(systemId));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public ExternalResolverConverter toExternalResolver() {
        return new ExternalResolverConverter();
    }

    public KubernetesSchemaResolver getKubernetesSchemaResolver() {
        return (KubernetesSchemaResolver) getSchemaResolver("kubernetes:");
    }

    public class ExternalResolverConverter {

        public ExternalResolver toExternalResolver() {
            return new ExternalResolver() {
                @Override
                public InputStream resolveAsFile(String filename, String baseDir) {
                    try {
                        if (baseDir != null) {
                            return ResolverMap.this.resolve(combine(baseDir, filename));
                        }
                        return ResolverMap.this.resolve(filename);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
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
}
