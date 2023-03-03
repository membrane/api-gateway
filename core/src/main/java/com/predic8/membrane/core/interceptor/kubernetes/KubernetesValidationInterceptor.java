/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.kubernetes.model.AdmissionResponse;
import com.predic8.membrane.core.interceptor.kubernetes.model.AdmissionReview;
import com.predic8.membrane.core.interceptor.kubernetes.model.JSONValidatorError;
import com.predic8.membrane.core.interceptor.kubernetes.model.ResponseStatus;
import com.predic8.membrane.core.interceptor.schemavalidation.IValidator;
import com.predic8.membrane.core.interceptor.schemavalidation.JSONValidator;
import com.predic8.membrane.core.resolver.ResolverMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @description
 * Kubernetes Integration is still experimental.
 * <p>
 * To create the CustomResourceDefinitions, apply kubernetes-config.yaml from
 * core/target/classes/com/predic8/membrane/core/config/kubernetes/ or a part (e.g. the 'serviceproxies' CRD) of the file.
 * <p>
 * Create a key and certificate for TLS for <a href="https://membrane-validator.membrane-soa.svc:444/">https://membrane-validator.membrane-soa.svc:444/</a> and setup Membrane to serve
 * this address. The configuration shown below configures Membrane on a fixed IP address outside of the Kubernetes cluster,
 * but this is no requirement.
 * <p>
 * Embed the following serviceProxy and adjust the 'resources' attribute to a comma-separated list of CRDs that you applied.
 * Note that while the CRDs have plural names, here you need to use the corresponding singular. Configure the "ssl" section
 * using your key and certificate.
 * <code>
 *    &gt;serviceProxy port="444">
 *      &gt;ssl>
 *        &gt;key>
 *          &gt;private>
 *            -----BEGIN RSA PRIVATE KEY-----
 *            ...
 *            -----END RSA PRIVATE KEY-----
 *          &gt;/private>
 *          &gt;certificate>
 *            -----BEGIN CERTIFICATE-----
 *            ...
 *            -----END CERTIFICATE-----
 *          &gt;/certificate>
 *        &gt;/key>
 *      &gt;/ssl>
 *      &gt;kubernetesValidation resources="serviceproxy" />
 *    &gt;/serviceProxy>
 * </code>
 * <p>
 * Now register a Webhook to validate the new CRDs. (A note to the experts: Membrane's validation schemas are too
 * complex to fit into the CRD, because they are highly nestable and self-referencing. We therefore use webhooks.)
 * <p>
 * <code>
 * apiVersion: admissionregistration.k8s.io/v1
 * kind: ValidatingWebhookConfiguration
 * metadata:
 *   name: membrane
 * webhooks:
 *   - name: membrane.membrane-soa.org
 *     admissionReviewVersions: ["v1", "v1beta1"]
 *     failurePolicy: Fail
 *     rules:
 *       - operations: [ "*" ]
 *         apiGroups: [ "membrane-soa.org" ]
 *         apiVersions: [ "v1", "v1beta1" ]
 *         resources: [ "*" ]
 *         scope: "*"
 *     clientConfig:
 *       service:
 *         name: membrane-validator
 *         namespace: membrane-soa
 *         port: 444
 *       caBundle: LS0t...LQ0K        # base64 encoded, PEM-formatted CA certificate
 *     sideEffects: None
 * <p>
 * ---
 * <p>
 * apiVersion: v1
 * kind: Namespace
 * metadata:
 *   name: membrane-soa
 * <p>
 * ---
 * <p>
 * apiVersion: v1
 * kind: Service
 * metadata:
 *   namespace: membrane-soa
 *   name: membrane-validator
 * spec:
 *   ports:
 *     - port: 444
 * <p>
 * ---
 * <p>
 * apiVersion: v1
 * kind: Endpoints
 * metadata:
 *   namespace: membrane-soa
 *   name: membrane-validator
 * subsets:
 *   - addresses:
 *       - ip: 192.168.0.1   # Membrane's IP
 *     ports:
 *       - port: 444
 * </code>
 * <p>
 * Once this setup is complete, you can enable serviceProxies like this:
 * <p>
 * <code>
 * apiVersion: membrane-soa.org/v1beta1
 * kind: serviceproxy
 * metadata:
 *   name: demo
 *   namespace: membrane-soa
 * spec:
 *   host: demo.predic8.de
 *   path:
 *     value: /some-path/
 *   interceptors:
 *     - response:
 *         interceptors:
 *         - groovy:
 *             src: |
 *               println "Hello!"
 *   target:
 *     host: thomas-bayer.com
 * </code>
 *
 */
@MCElement(name="kubernetesValidation")
public class KubernetesValidationInterceptor extends AbstractInterceptor {

    private ResolverMap resourceResolver;
    private List<String> resources;
    private ConcurrentMap<String, IValidator> validators = new ConcurrentHashMap<>();
    private List<String> namespaces = ImmutableList.of("membrane-soa");

    @Override
    public void init(Router router) throws Exception {
        resourceResolver = router.getResolverMap();
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (exc.getRequest().isBodyEmpty())
            return Outcome.CONTINUE;

        ObjectMapper mapper = new ObjectMapper();
        AdmissionReview review = mapper.readValue(new BufferedReader(new InputStreamReader(
                        exc.getRequest().getBodyAsStreamDecoded(), Charset.forName(exc.getRequest().getCharset())))
                , AdmissionReview.class);
        Map<String, Object> object = review.getRequest().getObject();
        if (object != null) { // DELETE requests do not carry an object
            String requestKind = (String) object.get("kind");

            IValidator validator = validators.computeIfAbsent(requestKind.toLowerCase(), schema -> {
                try {
                    return new JSONValidator(resourceResolver, "classpath:/com/predic8/membrane/core/config/kubernetes/" + schema + ".schema.json", null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            validator.validateMessage(exc, exc.getRequest(), "request");
        }
        setExchangeResponse(exc, mapper, review);

        return Outcome.RETURN;
    }


    private void setExchangeResponse(Exchange exc, ObjectMapper mapper, AdmissionReview review) throws Exception {
        AdmissionResponse response = new AdmissionResponse(review.getRequest().getUid());
        review.setResponse(response);

        if (exc.getResponse() == null) {
            response.setAllowed(true);
        } else {
            response.setAllowed(false);

            List<String> errors = mapper
                    .readValue(exc.getResponse().getBody().toString(), JSONValidatorError.class)
                    .getErrors();

            response.setStatus(new ResponseStatus(
                    String.join(", ", errors)
            ));
        }

        review.setRequest(null);
        exc.setResponse(Response.ok(
                mapper.writeValueAsString(review)
        ).build());
    }

    /**
     * @description The resources (CustomResourceDefinition Kinds, singular) to watch in the Kubernetes API, comma separated.
     * @example serviceproxy,ssl
     */
    @MCAttribute
    public void setResources(String resources) {
        this.resources = Arrays.asList(resources.split(","));
    }

    public String getResources() {
        return String.join(",", resources);
    }

    public List<String> getResourcesList() {
        return resources;
    }

    public List<String> getNamespacesList() {
        return namespaces;
    }

    public String getNamespaces() {
        return String.join(",", namespaces);
    }

    /**
     * @description The list of namespaces to watch, comma separated. A single '*' means "watch all namespaces".
     * @example *
     * @default membrane-soa
     */
    @MCAttribute
    public void setNamespaces(String namespaces) {
        this.namespaces = Arrays.asList(namespaces.split(","));
    }
}
