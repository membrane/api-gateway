package com.predic8.membrane.core.transport.ssl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.oracle.util.ssl.SSLCapabilities;
import com.oracle.util.ssl.SSLExplorer;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class GeneratingSSLContext extends SSLContext {
    private static final Log log = LogFactory.getLog(GeneratingSSLContext.class.getName());

    private final PrivateKey caPrivate;
    private final SSLParser sslParser;
    private final X509Certificate caPublic;

    LoadingCache<String, SSLContext> cache;

    public GeneratingSSLContext(SSLParser sslParser, ResolverMap resourceResolver, String baseLocation) {
        this.sslParser = sslParser;
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, "".toCharArray());

            List<Certificate> certs = new ArrayList<Certificate>();

            for (com.predic8.membrane.core.config.security.Certificate cert : sslParser.getKeyGenerator().getKey().getCertificates())
                certs.add(PEMSupport.getInstance().parseCertificate(cert.get(resourceResolver, baseLocation)));
            if (certs.size() == 0)
                throw new RuntimeException("At least one //ssl/keyGenerator/certificate is required.");

            checkChainValidity(certs);
            Object key = PEMSupport.getInstance().parseKey(sslParser.getKeyGenerator().getKey().getPrivate().get(resourceResolver, baseLocation));
            Key k = key instanceof Key ? (Key) key : ((KeyPair) key).getPrivate();
            if (k instanceof RSAPrivateCrtKey && certs.get(0).getPublicKey() instanceof RSAPublicKey) {
                RSAPrivateCrtKey privkey = (RSAPrivateCrtKey) k;
                RSAPublicKey pubkey = (RSAPublicKey) certs.get(0).getPublicKey();
                if (!(privkey.getModulus().equals(pubkey.getModulus()) && privkey.getPublicExponent().equals(pubkey.getPublicExponent())))
                    log.warn("Certificate does not fit to key.");

                caPrivate = privkey;
                caPublic = (X509Certificate) certs.get(0);
            } else {
                throw new RuntimeException("Key is a " + k.getClass().getName() + ", which is not yet supported.");
            }

            ks.setKeyEntry("inlinePemKeyAndCertificate", k, "".toCharArray(), certs.toArray(new Certificate[certs.size()]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            String keyPassword = "";
            if (sslParser.getKeyGenerator().getKey().getPassword() != null)
                keyPassword = sslParser.getKeyGenerator().getKey().getPassword();
            kmf.init(ks, keyPassword.toCharArray());

            cache = CacheBuilder.newBuilder().maximumSize(100).build(new CacheLoader<String, SSLContext>() {
                @Override
                public SSLContext load(String s) throws Exception {
                    log.info("Generating certificate for " + s);
                    return getSSLContextForHostname(s);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
        return new ServerSocket(port, 50, bindAddress);
    }

    @Override
    public Socket wrapAcceptedSocket(Socket socket) throws IOException {
        InputStream ins = socket.getInputStream();

        byte[] buffer = new byte[0xFF];
        int position = 0;
        SSLCapabilities capabilities = null;

        //Set socket read timeout to 30 seconds
        socket.setSoTimeout(30000);

        // Read the header of TLS record
        while (position < SSLExplorer.RECORD_HEADER_SIZE) {
            int count = SSLExplorer.RECORD_HEADER_SIZE - position;
            int n = ins.read(buffer, position, count);
            if (n < 0) {
                throw new IOException("unexpected end of stream!");
            }
            position += n;
        }

        // Get the required size to explore the SSL capabilities
        int recordLength = SSLExplorer.getRequiredSize(buffer, 0, position);
        if (buffer.length < recordLength) {
            buffer = Arrays.copyOf(buffer, recordLength);
        }

        while (position < recordLength) {
            int count = recordLength - position;
            int n = ins.read(buffer, position, count);
            if (n < 0) {
                throw new IOException("unexpected end of stream!");
            }
            position += n;
        }

        capabilities = SSLExplorer.explore(buffer, 0, recordLength);

        if (capabilities != null) {
            List<SNIServerName> serverNames = capabilities.getServerNames();
            if (serverNames != null && serverNames.size() > 0) {
                for (SNIServerName snisn : serverNames) {
                    String hostname = new String(snisn.getEncoded(), "UTF-8");
                    try {
                        return cache.get(hostname).wrap(socket, buffer, position);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        byte[] alert_unrecognized_name = { 21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
                2 /* fatal */, 112 /* unrecognized_name */ };

        try {
            socket.getOutputStream().write(alert_unrecognized_name);
        } finally {
            socket.close();
        }

        throw new RuntimeException("non-SNI connection not supported.");
    }

    public SSLContext getSSLContextForHostname(String hostname) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            org.bouncycastle.asn1.x500.X500Name xn = new org.bouncycastle.asn1.x500.X500Name("CN="+hostname);

            X509Certificate[] chain = new X509Certificate[]{
                    sign(xn.toString(), caPublic, caPrivate, kp.getPublic())
            };

            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);
            ks.setKeyEntry("alias", kp.getPrivate(), new char[0], chain);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);

            javax.net.ssl.SSLContext sslc = javax.net.ssl.SSLContext.getInstance("TLS");
            sslc.init(kmf.getKeyManagers(), null, null);

            return new StaticSSLContext(sslParser, sslc);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate sign(String subjectName, X509Certificate caPublic, PrivateKey caPrivate, PublicKey keyPublic)
            throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchProviderException, SignatureException, IOException,
            OperatorCreationException, CertificateException {

        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

        AsymmetricKeyParameter foo = PrivateKeyFactory.createKey(caPrivate.getEncoded());
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(keyPublic.getEncoded());

        org.bouncycastle.asn1.x500.X500Name caName = new JcaX509CertificateHolder(caPublic).getSubject();

        X509v3CertificateBuilder myCertificateGenerator = new X509v3CertificateBuilder(
                caName, new BigInteger("1"), new Date(
                System.currentTimeMillis() - 30 * 24 * 24 * 60 * 60 * 1000), new Date(
                System.currentTimeMillis() + 30 * 365 * 24 * 60 * 60
                        * 1000), new org.bouncycastle.asn1.x500.X500Name(subjectName), keyInfo);

        ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(foo);

        X509CertificateHolder holder = myCertificateGenerator.build(sigGen);
        org.bouncycastle.asn1.x509.Certificate eeX509CertificateStructure = holder.toASN1Structure();

        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

        // Read Certificate
        InputStream is1 = new ByteArrayInputStream(eeX509CertificateStructure.getEncoded());
        X509Certificate theCert = (X509Certificate) cf.generateCertificate(is1);
        is1.close();
        return theCert;
        //return null;
    }

    @Override
    public Socket createSocket(String host, int port, int connectTimeout) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress addr, int localPort, int connectTimeout) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    String getLocation() {
        return null;
    }

    @Override
    List<String> getDnsNames() {
        return Lists.newArrayList("*");
    }

    @Override
    public Socket wrap(Socket socket, byte[] buffer, int position) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    SSLSocketFactory getSocketFactory() {
        throw new IllegalStateException("not implemented");
    }
}
