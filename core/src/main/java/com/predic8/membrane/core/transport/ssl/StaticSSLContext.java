/* Copyright 2016 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http2.*;
import org.slf4j.*;

import javax.annotation.*;
import javax.crypto.*;
import javax.net.ssl.*;
import javax.validation.constraints.*;
import java.io.*;
import java.net.*;
import java.security.Key;
import java.security.KeyStore;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.*;

import static com.predic8.membrane.core.security.KeyStoreUtil.*;

public class StaticSSLContext extends SSLContext {

    private static final String DEFAULT_CERTIFICATE_SHA256_OLD = "c7:e3:fd:97:2f:d3:b9:4f:38:87:9c:45:32:70:b3:d8:c1:9f:d1:64:39:fc:48:5f:f4:a1:6a:95:b5:ca:08:f7";
    private static final String DEFAULT_CERTIFICATE_SHA256 = "5f:61:dc:8e:0b:5d:a4:50:65:d7:59:c9:d5:c3:22:49:5e:aa:91:c6:5a:c8:13:ac:51:6a:06:40:13:43:e8:f3";
    private static final Logger log = LoggerFactory.getLogger(StaticSSLContext.class.getName());
    public static final String PKCS_12 = "PKCS12";
    private static boolean defaultCertificateWarned = false;

    static {
        String dhKeySize = System.getProperty("jdk.tls.ephemeralDHKeySize");
        if (dhKeySize == null || "legacy".equals(dhKeySize))
            System.setProperty("jdk.tls.ephemeralDHKeySize", "matched");

        try {
            if (Cipher.getMaxAllowedKeyLength("AES") <= 128)
                log.warn("Your Java Virtual Machine does not have unlimited strength cryptography. If it is legal in your country, we strongly advise installing the Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files.");
        } catch (NoSuchAlgorithmException ignored) {
        }

        String enableStatusRequestExtension = System.getProperty("jdk.tls.server.enableStatusRequestExtension");
        if (enableStatusRequestExtension == null)
            System.setProperty("jdk.tls.server.enableStatusRequestExtension", "true");
    }

    private final SSLParser sslParser; // TODO push up to super class?
    private List<String> dnsNames;
    private javax.net.ssl.SSLContext sslc;

    private Validity validity;

    public StaticSSLContext(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) {
        if (sslParser.getTrustStore() != null && sslParser.getTrust() != null)
            throw new InvalidParameterException("<trust> may not be used together with <truststore>.");

        if (sslParser.getKeyStore() != null && sslParser.getKey() != null)
            throw new InvalidParameterException("<key> may not be used together with <keystore>.");

        this.sslParser = sslParser;

        try {
            initializeJavaSSLContext(createTrustManagerFactory(resourceResolver, baseLocation),
                    createKeyManagerFactoryWithSideEffects(resourceResolver, baseLocation));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(sslParser, sslc);
    }

    private @org.jetbrains.annotations.Nullable KeyManagerFactory createKeyManagerFactoryWithSideEffects( ResolverMap resourceResolver, String baseLocation) throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, NoSuchProviderException, UnrecoverableKeyException {
        if (sslParser.getKeyStore() != null) {
            char[] keyPass = getKeyPass(sslParser);

            KeyStore ks = openKeyStore(sslParser.getKeyStore(), keyPass, resourceResolver, baseLocation);

            String keyAlias = getKeyAlias(sslParser, ks);

            dnsNames = extractDnsNames(ks.getCertificate(keyAlias));
            validity = getValidityPeriod(ks, keyAlias);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(getAlgorithm(sslParser));
            kmf.init(filterKeyStoreByAlias(ks, keyPass, keyAlias), keyPass);
            return kmf;
        }
        if (sslParser.getKey() != null) {
            return getKeyManagerFactoryWithSideEffects(sslParser, resourceResolver, baseLocation);
        }
        return null; // Ok!
    }

    private @org.jetbrains.annotations.Nullable TrustManagerFactory createTrustManagerFactory( ResolverMap resourceResolver, String baseLocation) throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if (sslParser.getTrustStore() == null && sslParser.getTrust() == null)
            return null;

        KeyStore trustStore = null;
        String checkRevocation = null;
        if (sslParser.getTrustStore() != null) {
            trustStore = openKeyStore(sslParser.getTrustStore(), null, resourceResolver, baseLocation);
            checkRevocation = sslParser.getTrustStore().getCheckRevocation();
        } else if (sslParser.getTrust() != null) {
            trustStore = getStore(resourceResolver, baseLocation);
            checkRevocation = sslParser.getTrust().getCheckRevocation();
        }
        return createTrustManagerFactory2(trustStore, checkRevocation);
    }

    private @org.jetbrains.annotations.NotNull KeyStore getStore(ResolverMap resourceResolver, String baseLocation) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore trustStore = KeyStore.getInstance(PKCS_12);
        trustStore.load(null, "".toCharArray());

        for (int j = 0; j < sslParser.getTrust().getCertificateList().size(); j++)
            trustStore.setCertificateEntry("inlinePemCertificate" + j,
                    PEMSupport.getInstance().parseCertificate(
                            sslParser.getTrust().getCertificateList().get(j).get(resourceResolver, baseLocation)));
        return trustStore;
    }

    private String getTrustAlgorithm() {
        if (sslParser.getTrust() != null && sslParser.getTrust().getAlgorithm() != null)
            return sslParser.getTrust().getAlgorithm();

        if (sslParser.getTrustStore() != null && sslParser.getTrustStore().getAlgorithm() != null)
            return sslParser.getTrustStore().getAlgorithm();

        return TrustManagerFactory.getDefaultAlgorithm();
    }

    private @org.jetbrains.annotations.Nullable TrustManagerFactory createTrustManagerFactory2(KeyStore trustStore, String checkRevocation) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, KeyStoreException {
        TrustManagerFactory tmf = null;
        if (trustStore != null) {
            tmf = TrustManagerFactory.getInstance(getTrustAlgorithm());
            if (checkRevocation != null) {
                tmf.init( new CertPathTrustManagerParameters(getPkixBuilderParameters(trustStore, getTrustAlgorithm(), checkRevocation)) );
            } else {
                tmf.init(trustStore);
            }
        }
        return tmf;
    }

    private static @org.jetbrains.annotations.NotNull PKIXBuilderParameters getPkixBuilderParameters(KeyStore trustStore, String trustAlgorithm, String checkRevocation) throws KeyStoreException, InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustStore, new X509CertSelector());
        pkixParams.addCertPathChecker(getRevocationChecker(trustAlgorithm, checkRevocation));
        return pkixParams;
    }

    private static @org.jetbrains.annotations.NotNull PKIXRevocationChecker getRevocationChecker(String trustAlgorithm, String checkRevocation) throws NoSuchAlgorithmException {
        PKIXRevocationChecker rc = (PKIXRevocationChecker) CertPathBuilder.getInstance(trustAlgorithm).getRevocationChecker();
        rc.setOptions(createOptions(checkRevocation));
        return rc;
    }

    private static @org.jetbrains.annotations.NotNull EnumSet<PKIXRevocationChecker.Option> createOptions(String checkRevocation) {
        EnumSet<PKIXRevocationChecker.Option> options = EnumSet.noneOf(PKIXRevocationChecker.Option.class);
        for (String option : checkRevocation.split(","))
            options.add(PKIXRevocationChecker.Option.valueOf(option));
        return options;
    }

    private void initializeJavaSSLContext(TrustManagerFactory tmf, KeyManagerFactory kmf) throws KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] tms = tmf != null ? tmf.getTrustManagers() : null /* trust anyone: new TrustManager[] { new NullTrustManager() } */;
        if (sslParser.isIgnoreTimestampCheckFailure())
            tms = new TrustManager[] { new TrustManagerWrapper(tms, true) };

        if (sslParser.getProtocol() != null)
            sslc = javax.net.ssl.SSLContext.getInstance(sslParser.getProtocol());
        else
            sslc = javax.net.ssl.SSLContext.getInstance("TLS");

        sslc.init(
                kmf != null ? kmf.getKeyManagers() : null,
                tms,
                null);
    }

    private static String getKeyAlias(SSLParser sslParser, KeyStore ks) throws KeyStoreException {
        String paramAlias = sslParser.getKeyStore().getKeyAlias();
        return (paramAlias != null) ? aliasOrThrow(ks, paramAlias) : firstAliasOrThrow(ks);
    }

    record Validity(long from, long until) {}

    private Validity getValidityPeriod(KeyStore ks, String keyAlias) throws KeyStoreException {
        List<Certificate> certs = Arrays.asList(ks.getCertificateChain(keyAlias));
        return new Validity(getValidFrom(certs), getMinimumValidity(certs));
    }

    private @org.jetbrains.annotations.NotNull KeyManagerFactory getKeyManagerFactoryWithSideEffects(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {

        List<Certificate> certs = getCertificates(sslParser, resourceResolver, baseLocation);

        dnsNames = extractDnsNames(certs.getFirst());

        checkChainValidity(certs);
        validity = new Validity(getValidFrom(certs),getMinimumValidity(certs));

        return getKeyManagerFactory(sslParser, getKey(sslParser, resourceResolver, baseLocation, certs), certs);
    }

    private Key getKey(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation, List<Certificate> certs) throws IOException {
        Key k = getKey(sslParser, resourceResolver, baseLocation);
        checkKeyMatchesCert(k, certs);
        return k;
    }

    private @org.jetbrains.annotations.NotNull KeyManagerFactory getKeyManagerFactory(SSLParser sslParser, Key k, List<Certificate> certs) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(getKeyStore(k, certs), getKeyPassword(sslParser));
        return kmf;
    }

    private static @org.jetbrains.annotations.NotNull List<Certificate> getCertificates(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) throws IOException {
        List<Certificate> certs = new ArrayList<>();

        for (com.predic8.membrane.core.config.security.Certificate cert : sslParser.getKey().getCertificates())
            certs.add(PEMSupport.getInstance().parseCertificate(cert.get(resourceResolver, baseLocation)));
        if (certs.isEmpty())
            throw new RuntimeException("At least one //ssl/key/certificate is required.");
        return certs;
    }

    private static @org.jetbrains.annotations.NotNull KeyStore getKeyStore(Key k, List<Certificate> certs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks = KeyStore.getInstance(StaticSSLContext.PKCS_12);
        ks.load(null, "".toCharArray());
        ks.setKeyEntry("inlinePemKeyAndCertificate", k, "".toCharArray(),  certs.toArray(new Certificate[0]));
        return ks;
    }

    private static Key getKey(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) throws IOException {
        Object key = PEMSupport.getInstance().parseKey(sslParser.getKey().getPrivate().get(resourceResolver, baseLocation));
        return key instanceof Key? (Key) key : ((KeyPair)key).getPrivate();
    }

    private static char[] getKeyPassword(SSLParser sslParser) {
        if (sslParser.getKey().getPassword() != null)
            return sslParser.getKey().getPassword().toCharArray();
        return "".toCharArray();
    }

    private static char @org.jetbrains.annotations.NotNull [] getKeyPass(SSLParser sslParser) {
        char[] keyPass = "changeit".toCharArray();
        if (sslParser.getKeyStore().getKeyPassword() != null)
            keyPass = sslParser.getKeyStore().getKeyPassword().toCharArray();
        return keyPass;
    }

    private static String getAlgorithm(SSLParser sslParser) {
        if (sslParser.getAlgorithm() != null)
            return sslParser.getAlgorithm();
        return KeyManagerFactory.getDefaultAlgorithm();
    }

    public StaticSSLContext(SSLParser sslParser, javax.net.ssl.SSLContext sslc) {
        this.sslParser = sslParser;
        this.sslc = sslc;
        init(sslParser, sslc);
    }

    /**
     * Retrieves the DNS names from the specified certificate.
     *
     * @param certificate the certificate from which to extract DNS names
     * @return a list of DNS names found in the certificate
     * @throws CertificateParsingException if there is an error parsing the certificate
     */
    private List<String> extractDnsNames(Certificate certificate) throws CertificateParsingException {
        ArrayList<String> names = new ArrayList<>();
        if (certificate instanceof X509Certificate cert) {
            Collection<List<?>> subjectAlternativeNames = cert.getSubjectAlternativeNames();
            if (subjectAlternativeNames != null)
                for (List<?> l : subjectAlternativeNames) {
                    if (l.get(0) instanceof Integer idx && idx == 2)
                        names.add(l.get(1).toString());
                }
        }
        return names;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StaticSSLContext other))
            return false;
        return sslParser.hashCode() == other.sslParser.hashCode();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sslParser, dnsNames, sslc, validity);
    }

    public static KeyStore openKeyStore(Store store, char[] keyPass, ResolverMap resourceResolver, String baseLocation) throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, NoSuchProviderException {
        KeyStore ks = getAndLoadKeyStore(store, resourceResolver, baseLocation, getStoreTypeOrDefault(store), getPassword(store, keyPass));
        if (!defaultCertificateWarned && ks.getCertificate("membrane") != null) {
            if (getDigest(ks, "membrane").equals(DEFAULT_CERTIFICATE_SHA256_OLD) ||
                getDigest(ks, "membrane").equals(DEFAULT_CERTIFICATE_SHA256)) {
                log.warn("Using Membrane with the default certificate. This is highly discouraged! "
                        + "Please run the generate-ssl-keys script in the conf directory.");
                defaultCertificateWarned = true;
            }
        }
        return ks;
    }

    private static char @org.jetbrains.annotations.NotNull [] getPassword(Store store, char[] keyPass) {
        char[] password = keyPass;
        if (store.getPassword() != null)
            password = store.getPassword().toCharArray();
        if (password == null)
            throw new InvalidParameterException("Password for key store is not set.");
        return password;
    }

    private static @org.jetbrains.annotations.NotNull String getStoreTypeOrDefault(Store store) {
        String type = store.getType();
        if (type == null)
            return PKCS_12;
        return type;
    }

    public void applyCiphers(SSLServerSocket sslServerSocket) {
        if (ciphers != null) {
            SSLParameters sslParameters = sslServerSocket.getSSLParameters();
            applyCipherOrdering(sslParameters);
            sslParameters.setCipherSuites(ciphers);
            sslServerSocket.setSSLParameters(sslParameters);
        }
    }

    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
        SSLServerSocketFactory sslssf = sslc.getServerSocketFactory();
        SSLServerSocket sslss = (SSLServerSocket) sslssf.createServerSocket(port, backlog, bindAddress);
        applyCiphers(sslss);
        if (protocols != null) {
            sslss.setEnabledProtocols(protocols);
        } else {
            String[] protocols = sslss.getEnabledProtocols();
            Set<String> set = new HashSet<>();
            for (String protocol : protocols) {
                if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
                    continue;
                }
                set.add(protocol);
            }
            sslss.setEnabledProtocols(set.toArray(new String[0]));
        }
        sslss.setWantClientAuth(wantClientAuth);
        sslss.setNeedClientAuth(needClientAuth);

        if (sslParser.isUseExperimentalHttp2())
            Http2TlsSupport.offerHttp2(sslss);

        return sslss;
    }

    public Socket wrapAcceptedSocket(Socket socket) {
        return socket;
    }

    private void prepare(SSLSocket ssls) {
        if (protocols != null) {
            ssls.setEnabledProtocols(protocols);
        } else {
            String[] protocols = ssls.getEnabledProtocols();
            Set<String> set = new HashSet<>();
            for (String protocol : protocols) {
                if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
                    continue;
                }
                set.add(protocol);
            }
            ssls.setEnabledProtocols(set.toArray(new String[0]));
        }
        applyCiphers(ssls);
    }

    @Override
    public Socket createSocket() throws IOException {
        SSLSocket ssls = (SSLSocket) sslc.getSocketFactory().createSocket();
        prepare(ssls);
        return ssls;
    }

    public Socket createSocket(Socket socket, String host, int port, int connectTimeout, @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
        SSLSocketFactory sslsf = sslc.getSocketFactory();
        SSLSocket ssls = (SSLSocket) sslsf.createSocket(socket, host, port, true);
        applySNI(ssls, sniServerName,host);
        if (applicationProtocols != null)
            setApplicationProtocols(ssls, applicationProtocols);
        prepare(ssls);
        if (applicationProtocols != null)
            ssls.startHandshake();
        return ssls;
    }

    public Socket createSocket(String host, int port, int connectTimeout, @Nullable String sniServerName,
                               @Nullable String[] applicationProtocols) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeout);
        return createSocket(s, host, port, connectTimeout, sniServerName, applicationProtocols);
    }

    public Socket createSocket(String host, int port, InetAddress addr, int localPort, int connectTimeout,
                               @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
        Socket s = new Socket();
        s.bind(new InetSocketAddress(addr, localPort));
        s.connect(new InetSocketAddress(host, port), connectTimeout);
        return createSocket(s, host, port, connectTimeout, sniServerName, applicationProtocols);
    }

    private void applySNI(@NotNull SSLSocket ssls, @Nullable String sniServerName, @NotNull String defaultHost) {
        if(sniServerName != null && sniServerName.isEmpty())
            return;
        if(sniServerName == null)
            sniServerName = defaultHost;

        SNIHostName name = new SNIHostName(sniServerName.getBytes()); // mvn complains here when not putting in "bytes" even though there is a constructor for "string"
        List<SNIServerName> serverNames = new ArrayList<>(1);
        serverNames.add(name);

        SSLParameters params = ssls.getSSLParameters();
        params.setServerNames(serverNames);
        ssls.setSSLParameters(params);
    }

    private void setApplicationProtocols(@NotNull SSLSocket ssls, @NotNull String[] applicationProtocols) {
        SSLParameters sslp = ssls.getSSLParameters();
        sslp.setApplicationProtocols(applicationProtocols);
        ssls.setSSLParameters(sslp);
    }

    SSLSocketFactory getSocketFactory() {
        return sslc.getSocketFactory();
    }

    List<String> getDnsNames() {
        return dnsNames;
    }

    /**
     *
     * @return Human-readable description of there the keystore lives.
     */
    String getLocation() {
        return sslParser.getKeyStore() != null ? sslParser.getKeyStore().getLocation() : "null";
    }

    @Override
    public String getPrometheusContextTypeName() {
        return "static";
    }

    @Override
    public boolean hasKeyAndCertificate() {
        return validity.until != 0 && validity.from != 0;
    }

    @Override
    public long getValidFrom() {
        return validity.from;
    }

    @Override
    public long getValidUntil() {
        return validity.until;
    }
}
