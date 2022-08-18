package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.acme.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.kubernetes.client.KubernetesClientFactory;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.TimerManager;
import com.predic8.membrane.core.util.URIFactory;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.joda.time.Duration;
import org.jose4j.base64url.Base64;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.EcJwkGenerator;
import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.EllipticCurves;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.http.Header.LOCATION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JOSE_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;
import static com.predic8.membrane.core.transport.ssl.acme.Challenge.TYPE_DNS_01;
import static com.predic8.membrane.core.transport.ssl.acme.Challenge.TYPE_HTTP_01;
import static com.predic8.membrane.core.transport.ssl.acme.Identifier.TYPE_DNS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.lang.HashUtil.SHA_256;

public class AcmeClient {

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
    private String keyChangeUrl;
    private String newAccountUrl;
    private String newNonceUrl;
    private String newOrderUrl;
    private String revokeCertUrl;
    private List<String> contacts;
    private boolean termsOfServiceAgreed;
    private PrivateKey privateKey;
    private PublicJsonWebKey publicJsonWebKey;
    private String algorithm = AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256;
    private Duration validity;
    private AcmeSynchronizedStorageEngine asse;

    public AcmeClient(Acme acme, @Nullable HttpClientFactory httpClientFactory, @Nullable KubernetesClientFactory kubernetesClientFactory) {
        directoryUrl = acme.getDirectoryUrl();
        termsOfServiceAgreed = acme.isTermsOfServiceAgreed();
        contacts = Arrays.asList(acme.getContacts().split(" +"));
        if (httpClientFactory == null)
            httpClientFactory = new HttpClientFactory(null);
        hc = httpClientFactory.createClient(acme.getHttpClientConfiguration());
        validity = acme.getValidityDuration();
        challengeType = acme.getValidationMethod() != null && acme.getValidationMethod() instanceof DnsOperatorAcmeValidation ? TYPE_DNS_01 : TYPE_HTTP_01;

        om.registerModule(new JodaModule());

        AcmeSynchronizedStorage ass = acme.getAcmeSynchronizedStorage();
        if (ass == null) {
            throw new RuntimeException("<acme> is used, but to storage is configured.");
        } else if (ass instanceof FileStorage) {
            asse = new AcmeFileStorageEngine((FileStorage)ass);
        } else if (ass instanceof KubernetesStorage) {
            asse = new AcmeKubernetesStorageEngine((KubernetesStorage) ass, kubernetesClientFactory);
        } else if (ass instanceof MemoryStorage) {
            asse = new AcmeMemoryStorageEngine();
        } else {
            throw new RuntimeException("Unsupported: Storage type " + ass.getClass().getName());
        }

        if (!acme.isExperimental())
            throw new RuntimeException("The ACME client is still experimental, please set <acme experimental=\"true\" ... /> to acknowledge.");
    }

    public void loadDirectory() throws Exception {
        Exchange e = hc.call(new Request.Builder().get(directoryUrl).header("User-Agent", Constants.VERSION).buildExchange());
        handleError(e);

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
            String contentType = e.getResponse().getHeader().getFirstValue("Content-Type");
            if (APPLICATION_PROBLEM_JSON.equals(contentType)) {
                Map m = om.readValue(e.getResponse().getBodyAsStreamDecoded(), Map.class);
                String type = (String) m.get("type");
                String detail = (String) m.get("detail");
                List<Map> sub = (List<Map>) m.get("subproblems");
                String nonce = e.getResponse().getHeader().getFirstValue("Replay-Nonce");
                throw new AcmeException(type, detail, parse(sub), nonce);
            }
            throw new RuntimeException("ACME Server returned " + e.getResponse() + " " + e.getResponse().getBodyAsStringDecoded());
        }
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
        Exchange e = hc.call(new Request.Builder().method("HEAD").url(new URIFactory(), newNonceUrl).header("User-Agent", Constants.VERSION).buildExchange());
        handleError(e);

        String nonce = e.getResponse().getHeader().getFirstValue("Replay-Nonce");

        e.getResponse().getBodyAsStringDecoded();

        return nonce;
    }

    public AcmeKeyPair generateCertificateKey(String[] hosts) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            ECGenParameterSpec ecsp = new ECGenParameterSpec("secp384r1");
            kpg.initialize(ecsp, random);
            KeyPair kp = kpg.generateKeyPair();

            String key = "-----BEGIN EC PRIVATE KEY-----\n"
                    + Base64.encode(kp.getPrivate().getEncoded()) +
                    "\n-----END EC PRIVATE KEY-----\n";

            String pkey = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.encode(kp.getPublic().getEncoded()) +
                    "\n-----END PUBLIC KEY-----\n";

            return new AcmeKeyPair(pkey, key);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateCSR(String[] hosts, String privateKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            PrivateKey pk;
            try (PemReader pemReader = new PemReader(new StringReader(privateKey))) {
                PemObject pemObject = pemReader.readPemObject();
                byte[] content = pemObject.getContent();
                PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
                pk = factory.generatePrivate(privKeySpec);
            }
            PublicKey pubkey = computePublicKeyFromPrivate(pk);

            JcaPKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                    new X500Principal("CN=" + hosts[0]), pubkey);

            GeneralName[] altNames = new GeneralName[hosts.length];
            for (int i = 0; i < hosts.length; i++) {
                altNames[i] = new GeneralName(GeneralName.dNSName, hosts[i]);
            }

            ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();

            GeneralNames subjectAltNames = GeneralNames.getInstance(new DERSequence(altNames));
            extensionsGenerator.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

            p10Builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());

            JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withECDSA");
            ContentSigner signer = csBuilder.build(pk);
            PKCS10CertificationRequest csr2 = p10Builder.build(signer);

            StringWriter sw = new StringWriter();
            JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(sw);
            jcaPEMWriter.writeObject(csr2);
            jcaPEMWriter.close();

            return sw.toString()
                    .replaceAll("-----BEGIN CERTIFICATE REQUEST-----" + System.lineSeparator(), "")
                    .replaceAll(System.lineSeparator() + "-----END CERTIFICATE REQUEST-----", "")
                    .replaceAll(System.lineSeparator(), "")
                    .replaceAll("/", "_")
                    .replaceAll("\\+", "-")
                    .replaceAll("=", "");
        } catch (NoSuchAlgorithmException | IOException | OperatorCreationException | InvalidKeySpecException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
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
        if (!challenge.isPresent())
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

        ((AcmeKubernetesStorageEngine)asse).provisionDns(auth.getIdentifier().getValue(), record);
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
        MyJsonWebSignature jws = new MyJsonWebSignature();
        jws.setAlgorithmHeaderValue(algorithm);
        jws.setKey(getPrivateKey());
        jws.setHeader("nonce", nonce);
        jws.setHeader("url", url);
        c.call(jws);

        jws.sign();

        Map<String,Object> json = new LinkedHashMap<>();
        json.put("protected", jws.getEncodedHeader());
        json.put("payload", jws.getEncodedPayload());
        json.put("signature", jws.getEncodedSignature());
        String flatSer = JsonUtil.toJson(json);

        Exchange f = hc.call(new Request.Builder()
                .post(url)
                .header("Content-Type", APPLICATION_JOSE_JSON)
                .header("User-Agent", Constants.VERSION)
                .body(flatSer)
                .buildExchange());
        handleError(f);
        return f;
    }

    public interface HttpCallerWithNonce {
        Exchange call(String nonce) throws Exception;
    }

    public Exchange withNonce(HttpCallerWithNonce f) throws Exception {
        try {
            try {
                String nonce = getRememberedNonce();
                if (nonce == null)
                    nonce = retrieveNewNonce();
                Exchange e = f.call(nonce);
                rememberNonce(e.getResponse().getHeader().getFirstValue("Replay-Nonce"));
                return e;
            } catch (AcmeException ex) {
                if (AcmeException.TYPE_BAD_NONCE.equals(ex.getType())) {
                    Exchange e = f.call(ex.getNonce()); // Section 6.5.1: on 'badNonce' error, retry with nonce returned
                    rememberNonce(e.getResponse().getHeader().getFirstValue("Replay-Nonce"));
                    return e;
                }
                throw ex;
            }
        } catch (AcmeException ex) {
            rememberNonce(ex.getNonce());
            throw ex;
        }
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
        String notBefore, notAfter;
        if (validity != null) {
            Date now = new Date();
            synchronized (sdf) {
                notBefore = sdf.format(now);
                notAfter = sdf.format(new Date(now.getTime() + validity.getMillis()));
            }
        } else {
            notBefore = null;
            notAfter = null;
        }

        Exchange e = withNonce(nonce -> doJWSRequest(newOrderUrl, nonce, jws -> {
            HashMap<String, Object> pl = new HashMap<>();
            if (validity != null) {
                pl.put("notBefore", notBefore);
                pl.put("notAfter", notAfter);
            }
            pl.put("identifiers", hostnames.stream()
                    .map(host -> ImmutableMap.of("type", "dns", "value", host))
                    .collect(Collectors.toList()));

            String payload = om.writeValueAsString(pl);
            jws.setPayload(payload);
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        return new OrderAndLocation(parseOrder(e.getResponse()), e.getResponse().getHeader().getFirstValue(LOCATION));
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

    private Challenge parseChallenge(Response response) throws IOException {
        return om.readValue(response.getBodyAsStreamDecoded(), Challenge.class);
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

    public Challenge readyForChallenge(String accountUrl, String challengeUrl) throws Exception {
        Exchange e = withNonce(nonce -> doJWSRequest(challengeUrl, nonce, jws -> {
            jws.setPayload("{}");
            jws.setKeyIdHeaderValue(accountUrl);
        }));
        return parseChallenge(e.getResponse());
    }

    public String downloadCertificate(String accountUrl, String[] hosts, String certificateUrl) throws Exception {
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
