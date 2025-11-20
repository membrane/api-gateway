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
package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.*;
import com.predic8.membrane.core.azure.*;
import com.predic8.membrane.core.azure.api.dns.*;
import com.predic8.membrane.core.config.security.acme.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.*;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jcajce.provider.asymmetric.ec.*;
import org.bouncycastle.jce.provider.*;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.jcajce.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.*;
import org.bouncycastle.pkcs.*;
import org.bouncycastle.pkcs.jcajce.*;
import org.bouncycastle.util.io.pem.*;
import org.jetbrains.annotations.*;
import org.joda.time.*;
import org.jose4j.base64url.Base64;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.keys.*;
import org.jose4j.lang.*;
import org.slf4j.Logger;
import org.slf4j.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.module.SimpleModule;

import javax.annotation.Nullable;
import javax.security.auth.x500.*;
import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.text.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.transport.ssl.acme.Challenge.*;
import static com.predic8.membrane.core.transport.ssl.acme.Identifier.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static org.jose4j.lang.HashUtil.*;

public class AcmeClient {

    public static final String BEGIN_CERTIFICATE_REQUEST = "-----BEGIN CERTIFICATE REQUEST-----";
    public static final String END_CERTIFICATE_REQUEST = "-----END CERTIFICATE REQUEST-----";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final Logger LOG = LoggerFactory.getLogger(AcmeClient.class);
    private static final SecureRandom random = new SecureRandom();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final String directoryUrl;
    private final HttpClient hc;
    private final ObjectMapper om = new ObjectMapper();
    private final List<String> nonces = new ArrayList<>();
    private final String challengeType;
    private final AcmeSynchronizedStorage ass;
    private String keyChangeUrl;
    private String newAccountUrl;
    private String newNonceUrl;
    private String newOrderUrl;
    private String revokeCertUrl;
    private final List<String> contacts;
    private final boolean termsOfServiceAgreed;
    private PrivateKey privateKey;
    private PublicJsonWebKey publicJsonWebKey;
    private final String algorithm = AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256;
    private final Duration validity;
    private AcmeSynchronizedStorageEngine asse;
    private final AcmeValidation acmeValidation;

    public AcmeClient(Acme acme, @Nullable HttpClientFactory httpClientFactory) {
        directoryUrl = acme.getDirectoryUrl();
        termsOfServiceAgreed = acme.isTermsOfServiceAgreed();
        ass = acme.getAcmeSynchronizedStorage();
        contacts = Arrays.asList(acme.getContacts().split(" +"));
        if (httpClientFactory == null)
            httpClientFactory = new HttpClientFactory(null);
        hc = httpClientFactory.createClient(acme.getHttpClientConfiguration());
        validity = acme.getValidityDuration();
        this.acmeValidation = acme.getValidationMethod();
        challengeType = acme.getValidationMethod() != null && acme.getValidationMethod().useDnsValidation() ? TYPE_DNS_01 : TYPE_HTTP_01;

        if (!acme.isExperimental())
            throw new RuntimeException("The ACME client is still experimental, please set <acme experimental=\"true\" ... /> to acknowledge.");
    }

    public void init(@Nullable KubernetesClientFactory kubernetesClientFactory, @Nullable HttpClientFactory httpClientFactory) {
        switch (ass) {
            case null -> throw new RuntimeException("<acme> is used, but to storage is configured.");
            case FileStorage fileStorage -> asse = new AcmeFileStorageEngine(fileStorage);
            case KubernetesStorage kubernetesStorage ->
                    asse = new AcmeKubernetesStorageEngine(kubernetesStorage, kubernetesClientFactory);
            case MemoryStorage memoryStorage -> asse = new AcmeMemoryStorageEngine();
            case AzureTableStorage azureTableStorage ->
                    asse = new AcmeAzureTableApiStorageEngine(azureTableStorage, (AzureDns) acmeValidation, httpClientFactory);
            default -> throw new RuntimeException("Unsupported: Storage type " + ass.getClass().getName());
        }

        if (challengeType.equals(TYPE_DNS_01) && !(asse instanceof DnsProvisionable)) {
            throw new RuntimeException("A");
        }
    }

    public void loadDirectory() throws Exception {
        Exchange e = hc.call(new Request.Builder().get(directoryUrl).header("User-Agent", VERSION).buildExchange());
        handleError(e);

        @SuppressWarnings("rawtypes")
        Map dir = om.readValue(e.getResponse().getBodyAsStreamDecoded(), Map.class);

        keyChangeUrl = (String) dir.get("keyChange");
        newAccountUrl = (String) dir.get("newAccount");
        newNonceUrl = (String) dir.get("newNonce");
        newOrderUrl = (String) dir.get("newOrder");
        revokeCertUrl = (String) dir.get("revokeCert");
        // 'renewalInfo' not used
    }

    private void handleError(Exchange e) throws IOException, AcmeException {
        if (e.getResponse().getStatusCode() >= 300) {

            if (isOfMediaType(APPLICATION_PROBLEM_JSON, getContentType(e))) {
                @SuppressWarnings("rawtypes")
                Map m = om.readValue(e.getResponse().getBodyAsStreamDecoded(), Map.class);

                String type = (String) m.get("type");
                String detail = (String) m.get("detail");
                List<Map> sub = (List<Map>) m.get("subproblems");
                throw new AcmeException(type, detail, parse(sub), getReplayNonce(e));
            }
            throw new RuntimeException("ACME Server returned " + e.getResponse() + " " + e.getResponse().getBodyAsStringDecoded());
        }
    }

    private static String getContentType(Exchange e) {
        return e.getResponse().getHeader().getFirstValue(CONTENT_TYPE);
    }

    private List<AcmeException.SubProblem> parse(List<Map> subproblems) {
        if (subproblems == null)
            return null;
        return subproblems.stream().map(m ->
                new AcmeException.SubProblem(
                        (String) m.get("type"),
                        (String) m.get("detail"),
                        (Map)m.get("identifier"))).collect(Collectors.toList());
    }

    public String retrieveNewNonce() throws Exception {
        Exchange e = hc.call(createHeadRequest());
        handleError(e);
        String replayNonce = getReplayNonce(e);
        e.getResponse().getBodyAsStringDecoded(); // Do not delete! Probably to read the content of the body in order to work with keep alive
        return replayNonce;
    }

    private Exchange createHeadRequest() throws URISyntaxException {
        return new Request.Builder().method("HEAD").url(new URIFactory(), newNonceUrl).header(USER_AGENT, VERSION).buildExchange();
    }

    public AcmeKeyPair generateCertificateKey() {
        try {
            return getAcmeKeyPair(getKeyPairGenerator().generateKeyPair());
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull AcmeKeyPair getAcmeKeyPair(KeyPair kp) {
        return new AcmeKeyPair(getPublicKeyBase64Encoded(kp), getKeyBase64Encoded(kp));
    }

    private static @NotNull String getPublicKeyBase64Encoded(KeyPair kp) {
        return "-----BEGIN PUBLIC KEY-----\n"
               + Base64.encode(kp.getPublic().getEncoded()) +
               "\n-----END PUBLIC KEY-----\n";
    }

    private static @NotNull String getKeyBase64Encoded(KeyPair kp) {
        return "-----BEGIN EC PRIVATE KEY-----\n"
               + Base64.encode(kp.getPrivate().getEncoded()) +
               "\n-----END EC PRIVATE KEY-----\n";
    }

    private static @NotNull KeyPairGenerator getKeyPairGenerator() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(new ECGenParameterSpec("secp384r1"), random);
        return kpg;
    }

    public String generateCSR(String[] hosts, String privateKey) {
        try {
            PrivateKey pk = getPrivateKeyFromString(privateKey);

            JcaPKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                    new X500Principal("CN=" + hosts[0]), computePublicKeyFromPrivate(pk));

            p10Builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, getExtensions(GeneralNames.getInstance(new DERSequence(getGeneralNames(hosts)))));

            return formatCSR(convertCSR2String(getPkcs10CertificationRequest(pk, p10Builder)));
        } catch (NoSuchAlgorithmException | IOException | OperatorCreationException | InvalidKeySpecException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull String formatCSR(String sw) {
        return sw
                .replaceAll(BEGIN_CERTIFICATE_REQUEST + lineSeparator(), "")
                .replaceAll(lineSeparator() + END_CERTIFICATE_REQUEST, "")
                .replaceAll(lineSeparator(), "")
                .replaceAll("/", "_")
                .replaceAll("\\+", "-")
                .replaceAll("=", "");
    }

    private static @NotNull String convertCSR2String(PKCS10CertificationRequest csr2) throws IOException {
        StringWriter sw = new StringWriter();
        JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(sw);
        jcaPEMWriter.writeObject(csr2);
        jcaPEMWriter.close();
        return sw.toString();
    }

    private static PKCS10CertificationRequest getPkcs10CertificationRequest(PrivateKey pk, JcaPKCS10CertificationRequestBuilder p10Builder) throws OperatorCreationException {
        return p10Builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(pk));
    }

    private static Extensions getExtensions(GeneralNames subjectAltNames) throws IOException {
        ExtensionsGenerator generator = new ExtensionsGenerator();
        generator.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        return generator.generate();
    }

    private static GeneralName @NotNull [] getGeneralNames(String[] hosts) {
        return Arrays.stream(hosts)
                .map(host -> new GeneralName(GeneralName.dNSName, host))
                .toArray(GeneralName[]::new);
    }

    private static PrivateKey getPrivateKeyFromString(String privateKey) throws NoSuchAlgorithmException, NoSuchProviderException, IOException, InvalidKeySpecException {
        KeyFactory factory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        PrivateKey pk;
        try (PemReader pemReader = new PemReader(new StringReader(privateKey))) {
            PemObject pemObject = pemReader.readPemObject();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
            pk = factory.generatePrivate(privKeySpec);
        }
        return pk;
    }

    private PublicKey computePublicKeyFromPrivate(PrivateKey pk) {
        BCECPrivateKey pk1 = (BCECPrivateKey) pk;

        BigInteger d = pk1.getD();
        ECPoint q = pk1.getParameters().getG().multiply(d);
        return new BCECPublicKey("EC",
                new ECPublicKeySpec(q, pk1.getParameters()),
                BouncyCastleProvider.CONFIGURATION);
    }

    public String getToken(String host) {
        return asse.getToken(host);
    }

    public String provision(Authorization auth) throws Exception {
        Optional<Challenge> challenge = auth.getChallenges().stream().filter(c -> challengeType.equals(c.getType())).findAny();
        if (challenge.isEmpty())
            throw new RuntimeException("Could not find challenge of type "+challengeType+": " + om.writeValueAsString(auth));

        if (!TYPE_DNS.equals(auth.getIdentifier().getType()))
            throw new RuntimeException("Identifier type is not DNS: " + om.writeValueAsString(auth));

        if (TYPE_HTTP_01.equals(challengeType))
            provisionHttp(auth, challenge.get());
        else if (TYPE_DNS_01.equals(challengeType))
            provisionDns(auth, challenge.get());
        else
            throw new RuntimeException("Unimplemented challenge type handling " + challengeType);

        return challenge.get().getUrl();
    }

    private void provisionDns(Authorization auth, Challenge challenge) throws JoseException, NoSuchAlgorithmException {
        String keyAuth = challenge.getToken() + "." + getThumbprint();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String record = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(keyAuth.getBytes(UTF_8)));

        ((DnsProvisionable)asse).provisionDns(auth.getIdentifier().getValue(), record);
    }

    private void provisionHttp(Authorization auth, Challenge challenge) {
        asse.setToken(auth.getIdentifier().getValue(), challenge.token);
    }

    public String getChallengeType() {
        return challengeType;
    }

    public interface JWSParametrizer {
        void call(JsonWebSignature jws) throws Exception;
    }

    public Exchange doJWSRequest(String url, String nonce, JWSParametrizer c) throws Exception {
        Exchange f = createExchange(url, nonce, c);
        handleError(f);
        return f;
    }

    private Exchange createExchange(String url, String nonce, JWSParametrizer c) throws Exception {
        return hc.call(new Request.Builder()
                .post(url)
                .header(CONTENT_TYPE, APPLICATION_JOSE_JSON)
                .header(USER_AGENT, VERSION)
                .body(convert2String(getMyJsonWebSignature(url, nonce, c)))
                .buildExchange());
    }

    private static String convert2String(MyJsonWebSignature jws) {
        Map<String,Object> json = new LinkedHashMap<>();
        json.put("protected", jws.getEncodedHeader());
        json.put("payload", jws.getEncodedPayload());
        json.put("signature", jws.getEncodedSignature());
        return JsonUtil.toJson(json);
    }

    private @NotNull MyJsonWebSignature getMyJsonWebSignature(String url, String nonce, JWSParametrizer c) throws Exception {
        MyJsonWebSignature jws = new MyJsonWebSignature();
        jws.setAlgorithmHeaderValue(algorithm);
        jws.setKey(getPrivateKey());
        jws.setHeader("nonce", nonce);
        jws.setHeader("url", url);
        c.call(jws);
        jws.sign();
        return jws;
    }

    public interface HttpCallerWithNonce {
        Exchange call(String nonce) throws Exception;
    }

    public Exchange withNonce(HttpCallerWithNonce f) throws Exception {
        try {
            try {
                rememberNonce(getReplayNonce(f.call(getNonce())));
                return f.call(getNonce());
            } catch (AcmeException ex) {
                if (AcmeException.TYPE_BAD_NONCE.equals(ex.getType())) {
                    Exchange e = f.call(ex.getNonce()); // Section 6.5.1: on 'badNonce' error, retry with nonce returned
                    rememberNonce(getReplayNonce(e));
                    return e;
                }
                throw ex;
            }
        } catch (AcmeException ex) {
            rememberNonce(ex.getNonce());
            throw ex;
        }
    }

    private static String getReplayNonce(Exchange e) {
        return e.getResponse().getHeader().getFirstValue("Replay-Nonce");
    }

    private String getNonce() throws Exception {
        String nonce = getRememberedNonce();
        if (nonce == null)
            nonce = retrieveNewNonce();
        return nonce;
    }

    private String getRememberedNonce() {
        synchronized (nonces) {
            int size = nonces.size();
            if (size == 0)
                return null;
            return nonces.remove(size - 1);
        }
    }

    private void rememberNonce(@Nullable String nonce) {
        if (nonce != null) {
            synchronized (nonces) {
                nonces.add(nonce);
            }
        }
    }

    public String createAccount() throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(newAccountUrl, nonce, jws -> {
            HashMap<String, Object> pl = new HashMap<>();
            pl.put("termsOfServiceAgreed", termsOfServiceAgreed);
            pl.put("contact", contacts);
            String payload = om.writeValueAsString(pl);
            jws.setPayload(payload);
            jws.setJwkHeader(getPublicJwk());
        }));

        e.getResponse().getBodyAsStringDecoded();
        return e.getResponse().getHeader().getFirstValue("Location");
    }

    private PublicJsonWebKey getPublicJwk() throws JoseException {
        getPrivateKey(); // ensures that publicJWK is set
        return publicJsonWebKey;
    }

    public OrderAndLocation createOrder(String accountUrl, List<String> hostnames) throws Exception {
        return getOrderAndLocation(createExchange(accountUrl, hostnames, getNotBeforeNotAfter()));
    }

    private @NotNull OrderAndLocation getOrderAndLocation(Exchange e) throws IOException {
        return new OrderAndLocation(parseOrder(e.getResponse()), e.getResponse().getHeader().getFirstValue(LOCATION));
    }

    private Exchange createExchange(String accountUrl, List<String> hostnames, Pair<String, String> notBeforeNotAfter) throws Exception {
        return withNonce(nonce -> doJWSRequest(newOrderUrl, nonce, jws -> {
            HashMap<String, Object> pl = new HashMap<>();
            if (validity != null) {
                pl.put("notBefore", notBeforeNotAfter.first());
                pl.put("notAfter", notBeforeNotAfter.second());
            }
            pl.put("identifiers", getIdentifiers(hostnames));

            String payload = om.writeValueAsString(pl);
            jws.setPayload(payload);
            jws.setKeyIdHeaderValue(accountUrl);
        }));
    }

    private static @NotNull List<ImmutableMap<String, String>> getIdentifiers(List<String> hostnames) {
        return hostnames.stream()
                .map(host -> ImmutableMap.of("type", "dns", "value", host))
                .toList();
    }

    private @NotNull Pair<String, String> getNotBeforeNotAfter() {
        if (validity != null) {
            Date now = new Date();
            synchronized (sdf) {
                return new Pair<>(sdf.format(now), sdf.format(new Date(now.getTime() + validity.getMillis())) );
            }
        }
        return new Pair<>(null, null);
    }

    public OrderAndLocation getOrder(String accountUrl, String orderUrl) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(orderUrl, nonce, jws -> {
            jws.setPayload("");
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        return new OrderAndLocation(parseOrder(e.getResponse()), orderUrl);
    }

    private Order parseOrder(Response response) throws IOException {
        return om.readValue(response.getBodyAsStreamDecoded(), Order.class);
    }

    private void parseChallenge(Response response) throws IOException {
        om.readValue(response.getBodyAsStreamDecoded(), Challenge.class);
    }

    public Order finalizeOrder(String accountUrl, String finalizationUrl, String csr) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(finalizationUrl, nonce, jws -> {
            HashMap<String, Object> pl = new HashMap<>();
            pl.put("csr", csr);

            String payload = om.writeValueAsString(pl);
            jws.setPayload(payload);
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        return parseOrder(e.getResponse());
    }

    public Authorization getAuth(String accountUrl, String authUrl) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(authUrl, nonce, jws -> {
            jws.setPayload("");
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        return parseAuthorization(e.getResponse());
    }

    private Authorization parseAuthorization(Response response) throws IOException {
        return om.readValue(response.getBodyAsStreamDecoded(), Authorization.class);
    }

    /**
     * Maybe just a test if the JSON can be parsed?
     */
    public void readyForChallenge(String accountUrl, String challengeUrl) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(challengeUrl, nonce, jws -> {
            jws.setPayload("{}");
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        parseChallenge(e.getResponse());
    }

    public String downloadCertificate(String accountUrl, String certificateUrl) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(certificateUrl, nonce, jws -> {
            jws.setPayload("");
            jws.setKeyIdHeaderValue(accountUrl);
        }));

        return e.getResponse().getBodyAsStringDecoded();
    }

    public String getThumbprint() throws JoseException {
        return getPublicJwk().calculateBase64urlEncodedThumbprint(SHA_256);
    }

    private Key getPrivateKey() throws JoseException {
        String accountKey = asse.getAccountKey();
        if (accountKey != null) {
            EllipticCurveJsonWebKey ek = new EllipticCurveJsonWebKey(JsonUtil.parseJson(accountKey));
            privateKey = ek.getPrivateKey();
            publicJsonWebKey = ek;
        } else {
            if (LOG.isDebugEnabled())
                LOG.debug("acme: generating key");
            EllipticCurveJsonWebKey jwk = generateKey();
            privateKey = jwk.getPrivateKey();
            asse.setAccountKey(jwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE));
            publicJsonWebKey = jwk;
        }
        return privateKey;
    }

    private EllipticCurveJsonWebKey generateKey() throws JoseException {
        String spec = "P-256";

        EllipticCurveJsonWebKey jwk = EcJwkGenerator.generateJwk(EllipticCurves.getSpec(spec), null, random);
        jwk.setKeyId(new BigInteger(130, random).toString(32));
        jwk.setUse("sig");
        jwk.setAlgorithm(algorithm);
        return jwk;
    }

    public String getKey(String[] hosts) {
        return asse.getPrivateKey(hosts);
    }

    public String getCertificates(String[] hosts) {
        return asse.getCertChain(hosts);
    }

    private static class MyJsonWebSignature extends JsonWebSignature {
        @Override
        public String getEncodedHeader() {
            return super.getEncodedHeader();
        }
    }

    AcmeSynchronizedStorageEngine getAsse() {
        return asse;
    }

    public void ensureAccountKeyExists() throws JoseException {
        getPrivateKey();
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setOALKey(String[] hosts, AcmeKeyPair key) throws JsonProcessingException {
        asse.setOALKey(hosts, om.writeValueAsString(key));
    }

    public AcmeKeyPair getOALKey(String[] hosts) throws JsonProcessingException {
        String key = asse.getOALKey(hosts);
        if (key == null)
            return null;
        return om.readValue(key, AcmeKeyPair.class);
    }

    public void setOALError(String[] hosts, AcmeErrorLog acmeErrorLog) throws JsonProcessingException {
        asse.setOALError(hosts, om.writeValueAsString(acmeErrorLog));
    }

    public AcmeErrorLog getOALError(String[] hosts) throws JsonProcessingException {
        String error = asse.getOALError(hosts);
        if (error == null)
            return null;
        return om.readValue(error, AcmeErrorLog.class);
    }
}
