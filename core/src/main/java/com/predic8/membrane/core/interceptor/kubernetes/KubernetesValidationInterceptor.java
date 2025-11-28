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

import tools.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.kubernetes.model.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.resolver.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Kubernetes Integration is still experimental.
 * <p>
 * To create the CustomResourceDefinitions, apply kubernetes-config.yaml from
 * core/target/classes/com/predic8/membrane/core/config/kubernetes/ or a part (e.g. the 'serviceproxies' CRD) of the file.
 * </p>
 * <p>
 * Create a key and certificate for TLS for <a href="https://membrane-validator.membrane-soa.svc:444/">https://membrane-validator.membrane-soa.svc:444/</a> and setup Membrane to serve
 * this address. The configuration shown below configures Membrane on a fixed IP address outside of the Kubernetes cluster,
 * but this is no requirement.
 * </p>
 * <p>
 * Embed the following serviceProxy and adjust the 'resources' attribute to a comma-separated list of CRDs that you applied.
 * Note that while the CRDs have plural names, here you need to use the corresponding singular. Configure the "ssl" section
 * using your key and certificate.
 * </p>
 * <code>
 * &gt;serviceProxy port="444">
 * &gt;ssl>
 * &gt;key>
 * &gt;private>
 * -----BEGIN RSA PRIVATE KEY-----
 * ...
 * -----END RSA PRIVATE KEY-----
 * &gt;/private>
 * &gt;certificate>
 * -----BEGIN CERTIFICATE-----
 * ...
 * -----END CERTIFICATE-----
 * &gt;/certificate>
 * &gt;/key>
 * &gt;/ssl>
 * &gt;kubernetesValidation resources="serviceproxy" />
 * &gt;/serviceProxy>
 * </code>
 * <p>
 * Now register a Webhook to validate the new CRDs. (A note to the experts: Membrane's validation schemas are too
 * complex to fit into the CRD, because they are highly nestable and self-referencing. We therefore use webhooks.)
 * </p>
 * <code>
 * apiVersion: admissionregistration.k8s.io/v1
 * kind: ValidatingWebhookConfiguration
 * metadata:
 * name: membrane
 * webhooks:
 * - name: membrane.membrane-soa.org
 * admissionReviewVersions: ["v1", "v1beta1"]
 * failurePolicy: Fail
 * rules:
 * - operations: [ "*" ]
 * apiGroups: [ "membrane-soa.org" ]
 * apiVersions: [ "v1", "v1beta1" ]
 * resources: [ "*" ]
 * scope: "*"
 * clientConfig:
 * service:
 * name: membrane-validator
 * namespace: membrane-soa
 * port: 444
 * caBundle: LS0t...LQ0K        # base64 encoded, PEM-formatted CA certificate
 * sideEffects: None
 * ---
 * apiVersion: v1
 * kind: Namespace
 * metadata:
 * name: membrane-soa
 * ---
 * apiVersion: v1
 * kind: Service
 * metadata:
 * namespace: membrane-soa
 * name: membrane-validator
 * spec:
 * ports:
 * - port: 444
 * ---
 * apiVersion: v1
 * kind: Endpoints
 * metadata:
 * namespace: membrane-soa
 * name: membrane-validator
 * subsets:
 * - addresses:
 * - ip: 192.168.0.1   # Membrane's IP
 * ports:
 * - port: 444
 * </code>
 * <p>
 * Once this setup is complete, you can enable serviceProxies like this:
 * </p>
 * <code>
 * apiVersion: membrane-api.io/v1beta2
 * kind: serviceproxy
 * metadata:
 * name: demo
 * namespace: membrane-soa
 * spec:
 * host: demo.predic8.de
 * path:
 * value: /some-path/
 * interceptors:
 * - response:
 * interceptors:
 * - groovy:
 * src: |
 * println "Hello!"
 * target:
 * host: thomas-bayer.com
 * </code>
 */
@MCElement(name = "kubernetesValidation")
public class KubernetesValidationInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(KubernetesValidationInterceptor.class.getName());


    private ResolverMap resourceResolver;
    private List<String> resources;
    private final ConcurrentMap<String, MessageValidator> validators = new ConcurrentHashMap<>();
    private List<String> namespaces = ImmutableList.of("membrane-soa");

    @Override
    public void init() {
        super.init();
        resourceResolver = router.getResolverMap();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {


            if (exc.getRequest().isBodyEmpty())
                return CONTINUE;

            ObjectMapper mapper = new ObjectMapper();
            AdmissionReview review = mapper.readValue(exc.getRequest().getBodyAsStreamDecoded(), AdmissionReview.class);

            Map<String, Object> object = review.getRequest().getObject();
            if (object != null) { // DELETE requests do not carry an object
                String requestKind = (String) object.get("kind");

                MessageValidator validator = validators.computeIfAbsent(requestKind.toLowerCase(), schema -> new JSONSchemaValidator(resourceResolver, "classpath:/com/predic8/membrane/core/config/kubernetes/" + schema + ".schema.json", null));
                validator.validateMessage(exc, REQUEST);
            }
            setExchangeResponse(exc, mapper, review);

            return RETURN;
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(), getDisplayName())
                    .component(getDisplayName())
                    .detail("Error handling request!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
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
     * @example serviceproxy, ssl
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
