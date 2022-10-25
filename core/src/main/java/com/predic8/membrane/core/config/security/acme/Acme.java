/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.joda.time.Duration;

import java.util.Objects;

/**
 * @description
 * <p>Configures an ACME (RFC 8555) client, e.g. to retrieve TLS certificates from
 * <a href="https://letsencrypt.org/">Let's Encrypt</a>.</p>
 * <p>To store the key material and certificates, you can use the local file system or your Kubernetes cluster.</p>
 * <p>
 *     To use ACME:
 *     <ol>
 *         <li>Register your domain and point the DNS record to your server(s) runnig Membrane.</li>
 *         <li>Let Membrane listen on port 80 and use the <code>&lt;acmeHttpChallenge /&gt;</code> (see below).</li>
 *         <li>Let Membrane listen on port 443 and use <code>&lt;ssl&gt;&lt;acme .../&gt;&lt;ssl&gt;</code> (see below).
 *         Here, you have to configure where Membrane will store the keys and certificates.
 *         </li>
 *     </ol>
 * </p>
 * <code>
 * &lt;serviceProxy port="80"&gt;
 *     &lt;acmeHttpChallenge /&gt;
 *     &lt;groovy&gt;
 *         exc.setResponse(Response.ok("here").build());
 *         RETURN
 *     &lt;/groovy&gt;
 * &lt;/serviceProxy&gt;
 * </code>
 * <code>
 * &lt;serviceProxy host="test.example.com" port="443"&gt;
 *     &lt;ssl&gt;
 *         &lt;acme
 *                 directoryUrl="https://acme-staging-v02.api.letsencrypt.org/directory"
 *                 contacts="mailto:webmaster@example.com"
 *                 termsOfServiceAgreed="true"&gt;
 *             &lt;fileStorage dir="C:\tmp" /&gt;
 *         &lt;/acme&gt;
 *     &lt;/ssl&gt;
 *     ...
 * &lt;/serviceProxy&gt;
 * </code>
 * <code>
 * &lt;serviceProxy host="test.example.com" port="443"&gt;
 *     &lt;ssl&gt;
 *         &lt;acme
 *                 directoryUrl="https://acme-staging-v02.api.letsencrypt.org/directory"
 *                 contacts="mailto:webmaster@example.com"
 *                 termsOfServiceAgreed="true"&gt;
 *             &lt;kubernetesStorage namespace="membrane"
 *                 masterLease="membrane" accountSecret="acme-account" prefix="acme-" /&gt;
 *         &lt;/acme&gt;
 *     &lt;/ssl&gt;
 *     ...
 * &lt;/serviceProxy&gt;
 * </code>
 *
 * @topic 6. Security
 */
@MCElement(name="acme")
public class Acme {

    String directoryUrl;
    boolean termsOfServiceAgreed;
    String contacts;
    HttpClientConfiguration httpClientConfiguration;
    Duration validity;
    AcmeSynchronizedStorage acmeSynchronizedStorage;
    boolean experimental;
    String hosts;
    AcmeValidation validationMethod;
    String renewal = "1/3";
    int retry = 10000;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Acme acme = (Acme) o;
        return termsOfServiceAgreed == acme.termsOfServiceAgreed
                && experimental == acme.experimental
                && Objects.equals(directoryUrl, acme.directoryUrl)
                && Objects.equals(contacts, acme.contacts)
                && Objects.equals(httpClientConfiguration, acme.httpClientConfiguration)
                && Objects.equals(validity, acme.validity)
                && Objects.equals(acmeSynchronizedStorage, acme.acmeSynchronizedStorage)
                && Objects.equals(hosts, acme.hosts)
                && Objects.equals(validationMethod, acme.validationMethod)
                && Objects.equals(renewal, acme.renewal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directoryUrl,
                termsOfServiceAgreed,
                contacts,
                httpClientConfiguration,
                validity,
                acmeSynchronizedStorage,
                experimental,
                hosts,
                validationMethod,
                renewal);
    }

    public String getDirectoryUrl() {
        return directoryUrl;
    }

    @MCAttribute
    public void setDirectoryUrl(String directoryUrl) {
        this.directoryUrl = directoryUrl;
    }

    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCChildElement(order=10)
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public boolean isTermsOfServiceAgreed() {
        return termsOfServiceAgreed;
    }

    @MCAttribute
    public void setTermsOfServiceAgreed(boolean termsOfServiceAgreed) {
        this.termsOfServiceAgreed = termsOfServiceAgreed;
    }

    public String getContacts() {
        return contacts;
    }

    /**
     * @example mailto:webmaster@example.com
     */
    @MCAttribute
    public void setContacts(String contacts) {
        this.contacts = contacts;
    }

    public String getValidity() {
        return validity.toString();
    }

    /**
     * @example PT7D
     */
    @MCAttribute
    public void setValidity(String validity) {
        this.validity = validity == null ? null : Duration.parse(validity);
    }

    public Duration getValidityDuration() {
        return validity;
    }

    public AcmeSynchronizedStorage getAcmeSynchronizedStorage() {
        return acmeSynchronizedStorage;
    }

    @MCChildElement
    public void setAcmeSynchronizedStorage(AcmeSynchronizedStorage acmeSynchronizedStorage) {
        this.acmeSynchronizedStorage = acmeSynchronizedStorage;
    }

    public boolean isExperimental() {
        return experimental;
    }

    @MCAttribute
    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    public String getHosts() {
        return hosts;
    }

    @MCAttribute
    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getRenewal() {
        return renewal;
    }

    /**
     * @description
     * When to renew the certificate. Can be "1/3" (meaning that the certificate will be renewed when 1/3 of the
     * validity period is left) or "never" (meaning that renewal will never be attempted). If you choose "never",
     * someone else should take care of renewing the key and certificate and update the storage accordingly.
     * @default 1/3
     */
    @MCAttribute
    public void setRenewal(String renewal) {
        this.renewal = renewal;
    }

    public int getRetry() {
        return retry;
    }

    /**
     * @description
     * Number of milliseconds after which a retry should be attempted. (in case of any error, e.g. in case the
     * ACME server cannot be reached or validation failed.)
     * @default 10000
     */
    @MCAttribute
    public void setRetry(int retry) {
        this.retry = retry;
    }

    public AcmeValidation getValidationMethod() {
        return validationMethod;
    }

    @MCChildElement(order=20)
    public void setValidationMethod(AcmeValidation validationMethod) {
        this.validationMethod = validationMethod;
    }
}
