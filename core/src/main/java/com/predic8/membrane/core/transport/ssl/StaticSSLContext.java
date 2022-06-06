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

import com.google.common.base.Objects;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.Store;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.TrustManagerWrapper;
import com.predic8.membrane.core.transport.http2.Http2TlsSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.net.ssl.*;
import javax.validation.constraints.NotNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

public class StaticSSLContext extends SSLContext {

    private static final String DEFAULT_CERTIFICATE_SHA256 = "c7:e3:fd:97:2f:d3:b9:4f:38:87:9c:45:32:70:b3:d8:c1:9f:d1:64:39:fc:48:5f:f4:a1:6a:95:b5:ca:08:f7";
    private static final Logger log = LoggerFactory.getLogger(StaticSSLContext.class.getName());
    private static boolean default_certificate_warned = false;
    private static boolean limitedStrength;

    static {
        String dhKeySize = System.getProperty("jdk.tls.ephemeralDHKeySize");
        if (dhKeySize == null || "legacy".equals(dhKeySize))
            System.setProperty("jdk.tls.ephemeralDHKeySize", "matched");

        try {
            limitedStrength = Cipher.getMaxAllowedKeyLength("AES") <= 128;
            if (limitedStrength)
                log.warn("Your Java Virtual Machine does not have unlimited strength cryptography. If it is legal in your country, we strongly advise installing the Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files.");
        } catch (NoSuchAlgorithmException ignored) {
        }

        String enableStatusRequestExtension = System.getProperty("jdk.tls.server.enableStatusRequestExtension");
        if (enableStatusRequestExtension == null)
            System.setProperty("jdk.tls.server.enableStatusRequestExtension", "true");
    }


    private final SSLParser sslParser;
    private List<String> dnsNames;

    private javax.net.ssl.SSLContext sslc;


    public StaticSSLContext(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) {
        this.sslParser = sslParser;

        try {
            String algorihm = KeyManagerFactory.getDefaultAlgorithm();
            if (sslParser.getAlgorithm() != null)
                algorihm = sslParser.getAlgorithm();

            KeyManagerFactory kmf = null;
            String keyStoreType = "JKS";
            if (sslParser.getKeyStore() != null) {
                if (sslParser.getKeyStore().getKeyAlias() != null)
                    throw new InvalidParameterException("keyAlias is not yet supported.");
                char[] keyPass = "changeit".toCharArray();
                if (sslParser.getKeyStore().getKeyPassword() != null)
                    keyPass = sslParser.getKeyStore().getKeyPassword().toCharArray();

                if (sslParser.getKeyStore().getType() != null)
                    keyStoreType = sslParser.getKeyStore().getType();
                KeyStore ks = openKeyStore(sslParser.getKeyStore(), "JKS", keyPass, resourceResolver, baseLocation);
                kmf = KeyManagerFactory.getInstance(algorihm);
                kmf.init(ks, keyPass);

                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (ks.isKeyEntry(alias)) {
                        // first key is used by the KeyManagerFactory
                        dnsNames = getDNSNames(ks.getCertificate(alias));
                        break;
                    }
                }
            }
            if (sslParser.getKey() != null) {
                if (kmf != null)
                    throw new InvalidParameterException("<key> may not be used together with <keystore>.");

                KeyStore ks = KeyStore.getInstance(keyStoreType);
                ks.load(null, "".toCharArray());

                List<Certificate> certs = new ArrayList<Certificate>();

                for (com.predic8.membrane.core.config.security.Certificate cert : sslParser.getKey().getCertificates())
                    certs.add(PEMSupport.getInstance().parseCertificate(cert.get(resourceResolver, baseLocation)));
                if (certs.size() == 0)
                    throw new RuntimeException("At least one //ssl/key/certificate is required.");
                dnsNames = getDNSNames(certs.get(0));

                checkChainValidity(certs);
                Object key = PEMSupport.getInstance().parseKey(sslParser.getKey().getPrivate().get(resourceResolver, baseLocation));
                Key k = key instanceof Key ? (Key) key : ((KeyPair)key).getPrivate();
                checkKeyMatchesCert(k, certs);

                ks.setKeyEntry("inlinePemKeyAndCertificate", k, "".toCharArray(),  certs.toArray(new Certificate[certs.size()]));

                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                String keyPassword = "";
                if (sslParser.getKey().getPassword() != null)
                    keyPassword = sslParser.getKey().getPassword();
                kmf.init(ks, keyPassword.toCharArray());
            }

            TrustManagerFactory tmf = null;
            KeyStore ts = null;
            String trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            String checkRevocation = null;
            if (sslParser.getTrustStore() != null) {
                if (sslParser.getTrustStore().getAlgorithm() != null)
                    trustAlgorithm = sslParser.getTrustStore().getAlgorithm();
                ts = openKeyStore(sslParser.getTrustStore(), keyStoreType, null, resourceResolver, baseLocation);
                checkRevocation = sslParser.getTrustStore().getCheckRevocation();
            }
            if (sslParser.getTrust() != null) {
                if (tmf != null)
                    throw new InvalidParameterException("<trust> may not be used together with <truststore>.");
                if (sslParser.getTrust().getAlgorithm() != null)
                    trustAlgorithm = sslParser.getTrust().getAlgorithm();

                ts = KeyStore.getInstance(keyStoreType);
                ts.load(null, "".toCharArray());

                for (int j = 0; j < sslParser.getTrust().getCertificateList().size(); j++)
                    ts.setCertificateEntry("inlinePemCertificate" + j, PEMSupport.getInstance().parseCertificate(sslParser.getTrust().getCertificateList().get(j).get(resourceResolver, baseLocation)));
                checkRevocation = sslParser.getTrust().getCheckRevocation();
            }
            if (ts != null) {
                tmf = TrustManagerFactory.getInstance(trustAlgorithm);
                if (checkRevocation != null) {
                    CertPathBuilder cpb = CertPathBuilder.getInstance(trustAlgorithm);
                    PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector());
                    PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
                    EnumSet<PKIXRevocationChecker.Option> options = EnumSet.noneOf(PKIXRevocationChecker.Option.class);
                    for (String option : checkRevocation.split(","))
                        options.add(PKIXRevocationChecker.Option.valueOf(option));
                    pkixParams.addCertPathChecker(rc);
                    tmf.init( new CertPathTrustManagerParameters(pkixParams) );
                } else {
                    tmf.init(ts);
                }
            }

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

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(sslParser, sslc);
    }

    public StaticSSLContext(SSLParser sslParser, javax.net.ssl.SSLContext sslc) {
        this.sslParser = sslParser;
        this.sslc = sslc;
        init(sslParser, sslc);
    }

    private List<String> getDNSNames(Certificate certificate) throws CertificateParsingException {
        ArrayList<String> dnsNames = new ArrayList<String>();
        if (certificate instanceof X509Certificate) {
            X509Certificate x = (X509Certificate) certificate;

            Collection<List<?>> subjectAlternativeNames = x.getSubjectAlternativeNames();
            if (subjectAlternativeNames != null)
                for (List<?> l : subjectAlternativeNames) {
                    if (l.get(0) instanceof Integer && ((Integer)l.get(0) == 2))
                        dnsNames.add(l.get(1).toString());
                }
        }
        return dnsNames;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SSLContext))
            return false;
        StaticSSLContext other = (StaticSSLContext)obj;
        return Objects.equal(sslParser, other.sslParser);
    }

    private KeyStore openKeyStore(Store store, String defaultType, char[] keyPass, ResolverMap resourceResolver, String baseLocation) throws NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyStoreException, NoSuchProviderException {
        String type = store.getType();
        if (type == null)
            type = defaultType;
        char[] password = keyPass;
        if (store.getPassword() != null)
            password = store.getPassword().toCharArray();
        if (password == null)
            throw new InvalidParameterException("Password for key store is not set.");
        KeyStore ks;
        if (store.getProvider() != null)
            ks = KeyStore.getInstance(type, store.getProvider());
        else
            ks = KeyStore.getInstance(type);
        ks.load(resourceResolver.resolve(ResolverMap.combine(baseLocation, store.getLocation())), password);
        if (!default_certificate_warned && ks.getCertificate("membrane") != null) {
            byte[] pkeEnc = ks.getCertificate("membrane").getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(pkeEnc);
            byte[] mdbytes = md.digest();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < mdbytes.length; i++) {
                if (i > 0)
                    sb.append(':');
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            if (sb.toString().equals(DEFAULT_CERTIFICATE_SHA256)) {
                log.warn("Using Membrane with the default certificate. This is highly discouraged! "
                        + "Please run the generate-ssl-keys script in the conf directory.");
                default_certificate_warned = true;
            }
        }
        return ks;
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
            Set<String> set = new HashSet<String>();
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

    public Socket wrapAcceptedSocket(Socket socket) throws IOException {
        return socket;
    }

    private void prepare(SSLSocket ssls) {
        if (protocols != null) {
            ssls.setEnabledProtocols(protocols);
        } else {
            String[] protocols = ssls.getEnabledProtocols();
            Set<String> set = new HashSet<String>();
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
        if (setApplicationProtocols == null || getApplicationProtocols == null) {
            log.debug("Could not call setApplicationProtocols(), as method is not available. We will not be using ALPN.");
            return;
        }
        SSLParameters sslp = ssls.getSSLParameters();
        try {
            setApplicationProtocols.invoke(sslp, new Object[] { applicationProtocols });
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
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

}
