package com.predic8.membrane.core.kubernetes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.bouncycastle.util.Arrays;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_APPLY_PATCH_YAML;
import static java.nio.charset.StandardCharsets.UTF_8;

public class KubernetesClient {
    private final Consumer<Exchange> client;
    private final String baseURL;
    private final String namespace;
    private final Schema schema;
    private final ObjectMapper om;

    KubernetesClient(Consumer<Exchange> client, String baseURL, String namespace) {
        this.client = client;
        this.baseURL = baseURL;
        this.namespace = namespace == null ? "default" : namespace;
        this.om = new ObjectMapper();
        try {
            this.schema = Schema.getSchema(this, om);
        } catch (IOException | HttpException e) {
            throw new RuntimeException("could not initialize schema", e);
        }
    }

    public String version() throws HttpException, IOException {
        Exchange e;
        try {
            e = new Request.Builder().get(baseURL + "/version").buildExchange();
            client.call(e);
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        if (e.getResponse().getStatusCode() != 200)
            throw new HttpException(
                    e.getResponse().getStatusCode(),
                    e.getResponse().getStatusMessage() + " " + e.getResponse().getBodyAsStringDecoded());
        return e.getResponse().getBodyAsStringDecoded();
    }

    public Consumer<Exchange> getClient() {
        return client;
    }

    public String getBaseURL() {
        return baseURL;
    }

    /**
     * Lists the specified resources.
     *
     * Use this method only, if you do need not any information from the list structure. (e.g. no subsequent
     * call to {@link #watch(String, String, String, Long, ExecutorService, Watcher)}).
     *
     * @param apiVersion the resource apiVersion to list
     * @param kind the resource kind to list
     * @param namespace the resource namespace to list, or null if listing resources for all namespaces
     * @param batchSize size of the batches to fetch. (Note that this method always returns all items. The
     *                  batchSize only affects the size of the internal HTTP responses used by this method
     *                  to fetch the items.)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Stream<Map> listItems(String apiVersion, String kind, String namespace, int batchSize)
            throws IOException, KubernetesApiException {
        return list(apiVersion, kind, namespace, batchSize).flatMap(map -> ((List) map.get("items")).stream());
    }

    /**
     * Lists the specified resources.
     *
     * Use this method only, if you need some information from the list structure (e.g. the resourceVersion
     * to initialize a subsequent call to {@link #watch(String, String, String, Long, ExecutorService, Watcher)}).
     *
     * To only list the items, use {@link #listItems(String, String, String, int)} instead.
     *
     * To get the items of the list batches returned by this method, call
     * <code>list(...).flatMap(map -> ((List)map.get("items")).stream());</code>
     * @param apiVersion the resource apiVersion to list
     * @param kind the resource kind to list
     * @param namespace the resource namespace to list, or null if listing resources for all namespaces
     * @param batchSize size of the batches to return.
     */
    @SuppressWarnings({"rawtypes"})
    public Stream<Map> list(String apiVersion, String kind, String namespace, int batchSize)
            throws IOException, KubernetesApiException {
        String path = getPath("list", apiVersion, kind, namespace, null);

        Spliterator<Map> spliterator = new Spliterator<Map>() {
            boolean first = true;
            String _continue = null;

            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super Map> action) {
                if (!first && _continue == null)
                    return false;
                try {
                    Exchange e = new Request.Builder()
                            .get(baseURL + path + "?limit=" + batchSize +
                                    (_continue != null ? "&continue=" + _continue : ""))
                            .buildExchange();
                    doCall(new int[]{200}, e);

                    Map map = (Map) new JsonSlurper().parseText(e.getResponse().getBodyAsStringDecoded());
                    first = false;
                    _continue = (String) ((Map) map.get("metadata")).get("continue");
                    action.accept(map);
                    return true;
                } catch (URISyntaxException | IOException | KubernetesApiException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public Spliterator<Map> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return DISTINCT | NONNULL | IMMUTABLE;
            }
        };
        return StreamSupport.stream(spliterator, false);
    }

    /**
     *
     * @param apiVersion the apiVersion to watch
     * @param kind the kind to watch
     * @param namespace the namespace to watch (or null to watch all namespaces)
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException on a Kubernetes API problem
     */
    @SuppressWarnings({"rawtypes"})
    public void watch(String apiVersion, String kind, String namespace, Long resourceVersion, ExecutorService executors,
                      Watcher watcher) throws IOException, KubernetesApiException {
        String path = getPath("list", apiVersion, kind, namespace, null);
        try {
            Exchange e = new Request.Builder()
                    .get(baseURL + path + "?watch=1" +
                            (resourceVersion != null ? "&resourceVersion=" + resourceVersion : ""))
                    .buildExchange();
            doCall(new int[]{200}, e, false);

            executors.submit(() -> {
                try {
                    try (InputStream is = e.getResponse().getBodyAsStreamDecoded()) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF_8));
                        while (true) {
                            String line = br.readLine();
                            if (line == null)
                                break;
                            Map envelope = om.readValue(line, Map.class);
                            WatchAction action = WatchAction.valueOf((String) envelope.get("type"));
                            Map o = (Map) envelope.get("object");
                            watcher.onEvent(action, o);
                        }
                        watcher.onClosed(null);
                    }
                } catch (Throwable throwable) {
                    watcher.onClosed(throwable);
                }
            });
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     *
     * @param resource the resource to read
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException code 404 reason "NotFound", if resource does not exist
     */
    @SuppressWarnings({"rawtypes"})
    public Map read(Map resource) throws IOException, KubernetesApiException {
        return read(
                (String) resource.get("apiVersion"),
                (String) resource.get("kind"),
                (String) ((Map)resource.get("metadata")).get("namespace"),
                (String) ((Map)resource.get("metadata")).get("name"));
    }

    /**
     *
     * @param apiVersion the resource to read
     * @param kind the resource to read
     * @param namespace the resource to read
     * @param name the resource to read
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException code 404 reason "NotFound", if resource does not exist
     */
    @SuppressWarnings({"rawtypes"})
    public Map read(String apiVersion, String kind, String namespace, String name)
            throws IOException, KubernetesApiException {
        String path = getPath("read", apiVersion, kind, namespace, name);

        try {
            Exchange e = new Request.Builder()
                    .get(baseURL + path)
                    .buildExchange();
            doCall(new int[] { 200 }, e);

            return (Map) new JsonSlurper().parseText(e.getResponse().getBodyAsStringDecoded());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param resource the resource to create
     * @return the resource created
     * @throws IOException            an underlaying communication problem
     * @throws KubernetesApiException with code 409 reason "AlreadyExists", if the resource already exists
     */
    @SuppressWarnings({"rawtypes"})
    public Map create(Map resource) throws IOException, KubernetesApiException {
        String path = getPath("create",
                (String) resource.get("apiVersion"),
                (String) resource.get("kind"),
                (String) ((Map)resource.get("metadata")).get("namespace"),
                null);

        String body = JsonOutput.toJson(resource);
        try {
            Exchange e = new Request.Builder()
                    .post(baseURL + path + "?fieldManager=membrane")
                    .header(CONTENT_TYPE, MimeType.APPLICATION_JSON_UTF8)
                    .body(body).buildExchange();
            doCall(new int[] { 201 }, e);

            return (Map) new JsonSlurper().parseText(e.getResponse().getBodyAsStringDecoded());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     *
     * @param resource the resource to delete
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException with code 404 and reason NotFound if it does not exist
     */
    @SuppressWarnings({"rawtypes"})
    public void delete(Map resource) throws IOException, KubernetesApiException {
        delete(
                (String) resource.get("apiVersion"),
                (String) resource.get("kind"),
                (String) ((Map) resource.get("metadata")).get("namespace"),
                (String) ((Map) resource.get("metadata")).get("name"));
    }

    /**
     *
     * @param apiVersion the resource to delete
     * @param kind the resource to delete
     * @param namespace the resource to delete
     * @param name the resource to delete
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException with code 404 and reason NotFound if it does not exist
     */
    public void delete(String apiVersion, String kind, String namespace, String name)
            throws IOException, KubernetesApiException {
        String path = getPath("read", apiVersion, kind, namespace, name);

        try {
            doCall(new int[] { 200 }, new Request.Builder()
                    .delete(baseURL + path)
                    .buildExchange());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void patch(String apiVersion, String kind, String namespace, String name, String contentType, Object body)
            throws IOException, KubernetesApiException {
        String path = getPath("patch", apiVersion, kind, namespace, name);

        String bodyJ = JsonOutput.toJson(body);
        try {
            doCall(new int[] { 200 }, new Request.Builder()
                    .header(CONTENT_TYPE, contentType)
                    .url(new URIFactory(), baseURL + path)
                    .method("PATCH")
                    .body(bodyJ)
                    .buildExchange());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     *
     * @param resource the resource to apply
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException on a Kubernetes API problem
     */
    @SuppressWarnings({"rawtypes"})
    public void apply(Map resource) throws IOException, KubernetesApiException {
        String path = getPath("patch",
                (String) resource.get("apiVersion"),
                (String) resource.get("kind"),
                (String) ((Map) resource.get("metadata")).get("namespace"),
                (String) ((Map) resource.get("metadata")).get("name"));

        String body = JsonOutput.toJson(resource);
        try {
            doCall(new int[] { 200, 201 }, new Request.Builder()
                    .url(new URIFactory(), baseURL + path + "?fieldManager=membrane&force=false")
                    .method("PATCH")
                    .header(CONTENT_TYPE, APPLICATION_APPLY_PATCH_YAML)
                    .body(body)
                    .buildExchange());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     *
     * @param resource the resource to edit
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException code 409 reason "Conflict", if someone else modified the resource; code 404 reason
     * "NotFound", if resource does not exist.
     */
    @SuppressWarnings({"rawtypes"})
    public void edit(Map resource, java.util.function.Consumer<Map> editor) throws IOException, KubernetesApiException {
        Map m = read(resource);
        editor.accept(m);
        ((Map)m.get("metadata")).remove("managedFields");
        apply(m);
    }

    /**
     *
     * @param apiVersion the resource to edit
     * @param kind the resource to edit
     * @param namespace the resource to edit
     * @param name the resource to edit
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException code 409 reason "Conflict", if someone else modified the resource; code 404 reason
     * "NotFound", if resource does not exist.
     */
    @SuppressWarnings({"rawtypes"})
    public void edit(String apiVersion, String kind, String namespace, String name,
                     java.util.function.Consumer<Map> editor) throws IOException, KubernetesApiException {
        Map m = read(apiVersion, kind, namespace, name);
        editor.accept(m);
        ((Map)m.get("metadata")).remove("managedFields");
        apply(m);
    }

    /**
     * Ensures that
     * a) the resource exists (by reading and possibly creating it) and
     * b) the resource is edited.
     *
     * This method helps with compare-and-swap "CAS" semantics in the Kubernetes API.
     *
     * The create-and-edit sequence might seem unnecessary, but it looks this way (tested on 1.23.6): If you 'create'
     * a resource with values (e.g. entries in a Secret (below .data) or Lease (below .spec)), you own these values with
     * the operation 'Update' tracked in the managedFields structure. These values cannot (out of the box, that is
     * without re-owning the fields or manipulating the managedFields) be modified by a subsequent 'apply'.
     *
     * If you were to only use 'apply' (for creating and updating the resource), the fieldmanager is OK (operation
     * 'Apply' is tracked in managedFields). But this breaks the CAS semantics, as two concurrent creators have
     * different views of how they want the resource to look like. The first creator (=applier) succeeds (returning
     * HTTP 201), but the second creator (=applier) also succeeds (returning HTTP 200), effectively overriding the
     * first.
     *
     * Therefore, to archive correct CAS semantics, using this method,
     * a) create an empty resource
     * b) edit it to the state you want.
     *
     * Throws an error, if resource creation fails for any reason other than "AlreadyExists".
     * Throws an error, if editing the resource fails.
     *
     * @param resource the resource to create and edit
     * @throws IOException an underlaying communication problem
     * @throws KubernetesApiException code 409 reason "Conflict", if someone else modified the object; code 404, if
     * someone deleted the resource while we attempted to edit it.
     */
    @SuppressWarnings({"rawtypes"})
    public void createAndEdit(Map resource, java.util.function.Consumer<Map> editor)
            throws IOException, KubernetesApiException {
        Map m = null;
        while (m == null) {
            try {
                m = read(resource);
            } catch (KubernetesApiException e) {
                if (e.getCode() != 404 || !"NotFound".equals(e.getReason()))
                    throw e;
            }
            if (m == null) {
                try {
                    m = create(resource);
                    break;
                } catch (KubernetesApiException e) {
                    if (e.getCode() != 409 || !"AlreadyExists".equals(e.getReason()))
                        throw e;
                }
            }
        }
        editor.accept(m);
        ((Map)m.get("metadata")).remove("managedFields");
        apply(m);
    }

    private String getPath(String verb, String apiVersion, String kind, String namespace, String name) {
        String path = schema.getPath(verb, apiVersion, kind, namespace == null);
        if (path.contains("{namespace}")) {
            if (namespace == null)
                throw new IllegalArgumentException("The path " + path + " contains a namespace placeholder, but no " +
                        "namespace was provided.");
            path = path.replaceAll("\\{namespace}", namespace);
        }
        if (path.contains("{name}")) {
            if (name == null)
                throw new IllegalArgumentException("The path " + path + " contains a name placeholder, "+
                        "but no name was provided.");
            path = path.replaceAll("\\{name}", name);
        }
        return path;
    }

    private void doCall(int[] expectedHttpCode, Exchange e) throws KubernetesApiException, IOException {
        doCall(expectedHttpCode, e, true);
    }

    @SuppressWarnings({"rawtypes"})
    private void doCall(int[] expectedHttpCode, Exchange e, boolean fullyReadBody)
            throws KubernetesApiException, IOException {
        try {
            client.call(e);
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        if (fullyReadBody)
            e.getResponse().getBodyAsStreamDecoded();

        if (Arrays.contains(expectedHttpCode, e.getResponse().getStatusCode()))
            return;

        Map rbody = om.readValue(e.getResponse().getBodyAsStreamDecoded(), Map.class);

        throw new KubernetesApiException(e.getResponse().getStatusCode(), rbody);
    }

    public String getNamespace() {
        return namespace;
    }
}
