/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.google.common.collect.Sets;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.http2.Http2TlsSupport;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.math.ec.ECMultiplier;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.util.*;

public abstract class SSLContext implements SSLProvider {
    private static final Logger log = LoggerFactory.getLogger(SSLContext.class.getName());

    protected String[] ciphers;
    protected String[] protocols;
    protected boolean wantClientAuth, needClientAuth;
    protected String endpointIdentificationAlgorithm;

    private boolean showSSLExceptions = true;
    private boolean useAsDefault;
    private boolean useHttp2;

    public void init(SSLParser sslParser, javax.net.ssl.SSLContext sslc) {
        showSSLExceptions = sslParser.isShowSSLExceptions();
        useAsDefault = sslParser.isUseAsDefault();
        if (sslParser.getCiphers() != null) {
            ciphers = sslParser.getCiphers().split(",");
            Set<String> supportedCiphers = Sets.newHashSet(sslc.getSocketFactory().getSupportedCipherSuites());
            for (String cipher : ciphers) {
                if (!supportedCiphers.contains(cipher))
                    throw new InvalidParameterException("Unknown cipher " + cipher);
                if (cipher.contains("_RC4_"))
                    log.warn("Cipher {} uses RC4, which is deprecated.", cipher);
                if (cipher.contains("_3DES_"))
                    log.warn("Cipher {} uses 3DES, which is deprecated.", cipher);
            }
        } else {
            // use all default ciphers except those using RC4
            ciphers = Arrays.stream(sslc.getSocketFactory().getDefaultCipherSuites())
                    .filter(cipher -> (!cipher.contains("_RC4_") && !cipher.contains("_3DES_")))
                    .map(CipherInfo::new)
                    .sorted((ci1, ci2) -> ci2.points - ci1.points)
                    .map(cipherInfo -> cipherInfo.cipher)
                    .toArray(String[]::new);
        }

        if (sslParser.getProtocols() != null) {
            protocols = sslParser.getProtocols().split(",");
        } else {
            protocols = null;
        }

        if (sslParser.getClientAuth() == null) {
            needClientAuth = false;
            wantClientAuth = false;
        } else if (sslParser.getClientAuth().equals("need")) {
            needClientAuth = true;
            wantClientAuth = true;
        } else if (sslParser.getClientAuth().equals("want")) {
            needClientAuth = false;
            wantClientAuth = true;
        } else {
            throw new RuntimeException("Invalid value '"+sslParser.getClientAuth()+"' in clientAuth: expected 'want', 'need' or not set.");
        }

        endpointIdentificationAlgorithm = sslParser.getEndpointIdentificationAlgorithm();
        useHttp2 = sslParser.isUseExperimentalHttp2();
    }

    abstract String getLocation();
    abstract List<String> getDnsNames();

    public Socket wrap(Socket socket, byte[] buffer, int position) throws IOException {
        SSLSocketFactory serviceSocketFac = getSocketFactory();

        ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, position);

        SSLSocket serviceSocket = (SSLSocket)serviceSocketFac.createSocket(socket, bais, true);

        applyCiphers(serviceSocket);
        if (getProtocols() != null) {
            serviceSocket.setEnabledProtocols(getProtocols());
        } else {
            serviceSocket.setEnabledProtocols(
                    Arrays.stream(serviceSocket.getEnabledProtocols())
                            .filter(protocol -> !(protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")))
                            .toArray(String[]::new)
            );
        }
        serviceSocket.setWantClientAuth(isWantClientAuth());
        serviceSocket.setNeedClientAuth(isNeedClientAuth());
        if (useHttp2)
            Http2TlsSupport.offerHttp2(serviceSocket);
        return serviceSocket;
    }

    public void applyCiphers(SSLSocket sslSocket) {
        if (ciphers != null) {
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            applyCipherOrdering(sslParameters);
            sslParameters.setCipherSuites(ciphers);
            sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
            sslSocket.setSSLParameters(sslParameters);
        }
    }

    protected void applyCipherOrdering(SSLParameters sslParameters) {
        sslParameters.setUseCipherSuitesOrder(true);
    }

    String[] getCiphers() {
        return ciphers;
    }

    String[] getProtocols() {
        return protocols;
    }

    boolean isNeedClientAuth() {
        return needClientAuth;
    }

    boolean isWantClientAuth() {
        return wantClientAuth;
    }

    public String constructHostNamePattern() {
        List<String> dnsNames = getDnsNames();
        if (dnsNames == null)
            throw new RuntimeException("Could not extract DNS names from the first key's certificate in " + getLocation());
        if (dnsNames.isEmpty()) {
            log.warn("Could not retrieve DNS hostname for certificate, using '*': {}", getLocation());
            return "*";
        }
        return String.join(" ", dnsNames);
    }

    private static class CipherInfo {
        public final String cipher;
        public final int points;

        public CipherInfo(String cipher) {
            this.cipher = cipher;
            this.points = calculatePoints(cipher);
        }

        private int calculatePoints(String cipher) {
            int points = 0;
            if (supportsPFS(cipher))
                points += 100;
            if (supportsAESGCM(cipher))
                points += 15;
            if (!supportsAESCBC(cipher))
                points += 150;
            points += getAESStrength(cipher) * 5;
            points += getSHAStrength(cipher) * 2;
            points += getChaChaPoly1305Strength(cipher) * 25;
            return points;
        }

        private boolean supportsAESGCM(String cipher) {
            return cipher.contains("_GCM_");
        }

        private boolean supportsAESCBC(String cipher) {
            return cipher.contains("_CBC_");
        }

        private int getChaChaPoly1305Strength(String cipher) {
            if (cipher.contains("_CHACHA20_POLY1305_"))
                return 1;
            return 0;
        }

        private int getAESStrength(String cipher) {
            if (cipher.contains("_AES_512_"))
                return 2;
            if (cipher.contains("_AES_256_"))
                return 1;
            if (cipher.contains("_AES_128_"))
                return 0;
            return 0;
        }

        private int getSHAStrength(String cipher) {
            if (cipher.endsWith("_SHA384"))
                return 2;
            if (cipher.endsWith("_SHA256"))
                return 1;
            return 0;
        }

        private boolean supportsPFS(String cipher) {
            // see https://en.wikipedia.org/wiki/Forward_secrecy#Protocols
            return cipher.contains("_DHE_RSA_") || cipher.contains("_DHE_DSS_") || cipher.contains("_ECDHE_RSA_") || cipher.contains("_ECDHE_ECDSA_");
        }
    }

    abstract SSLSocketFactory getSocketFactory();

    protected void checkChainValidity(List<Certificate> certs) {
        boolean valid = true;
        for (int i = 0; i < certs.size() - 1; i++) {
            String currentIssuer = ((X509Certificate)certs.get(i)).getIssuerX500Principal().toString();
            String nextSubject = ((X509Certificate)certs.get(i+1)).getSubjectX500Principal().toString();
            valid = valid && com.google.common.base.Objects.equal(currentIssuer, nextSubject);
        }
        if (!valid) {
            StringBuilder sb = new StringBuilder();
            sb.append("Certificate chain is not valid:\n");
            for (int i = 0; i < certs.size(); i++) {
                sb.append("Cert %2d: Subject: %s\n".formatted(i, ((X509Certificate) certs.get(i)).getSubjectX500Principal()));
                sb.append("         Issuer: %s\n".formatted(((X509Certificate) certs.get(i)).getIssuerX500Principal()));
            }
            log.warn(sb.toString());
        }
    }

    @Override
    public boolean showSSLExceptions(){
        return showSSLExceptions;
    }

    public boolean isUseAsDefault() {
        return useAsDefault;
    }

    @Override
    public String[] getApplicationProtocols(Socket socket) {
        if (!(socket instanceof SSLSocket))
            return null;
        return ((SSLSocket)socket).getSSLParameters().getApplicationProtocols();
    }

    protected void checkKeyMatchesCert(Key key, List<Certificate> certs) {
        if (key instanceof RSAPrivateCrtKey privKey && certs.getFirst().getPublicKey() instanceof RSAPublicKey pubKey) {
            if (!(privKey.getModulus().equals(pubKey.getModulus()) && privKey.getPublicExponent().equals(pubKey.getPublicExponent())))
                throw new RuntimeException("Certificate does not fit to key: " + getLocation());
        }

        if (key instanceof ECPrivateKey privKey && certs.getFirst().getPublicKey() instanceof ECPublicKey pubKey) {

            if (pubKey.getParams().getCurve().getField() instanceof ECFieldFp pubField) {
                if (!(privKey.getParams().getCurve().getField() instanceof ECFieldFp privField))
                    throw new RuntimeException("Elliptic curve differs between private key and public key (ECFieldFp vs ECFieldF2m).");
                if (!pubField.getP().equals(privField.getP()))
                    throw new RuntimeException("Elliptic curve differs between private key and public key (p).");
            }
            // "pubKey.getParams().getCurve().getField() instanceof ECFieldF2m" is not handled

            if (!pubKey.getParams().getCurve().getA().equals(privKey.getParams().getCurve().getA()))
                throw new RuntimeException("Elliptic curve differs between private key and public key (a).");
            if (!pubKey.getParams().getCurve().getB().equals(privKey.getParams().getCurve().getB()))
                throw new RuntimeException("Elliptic curve differs between private key and public key (b).");
            if (!pubKey.getParams().getGenerator().equals(privKey.getParams().getGenerator())) // = G = (x,y)
                throw new RuntimeException("Elliptic curve differs between private key and public key (generator).");
            if (!pubKey.getParams().getOrder().equals(privKey.getParams().getOrder())) // = n
                throw new RuntimeException("Elliptic curve differs between private key and public key (order).");
            if (pubKey.getParams().getCofactor() != privKey.getParams().getCofactor()) // = h
                throw new RuntimeException("Elliptic curve differs between private key and public key (cofactor).");

            ECMultiplier ecMultiplier = new FixedPointCombMultiplier();

            ECPoint correspondingPubKey = ecMultiplier.multiply(((BCECPublicKey) pubKey).getParameters().getG(), privKey.getS()).normalize();

            // check 'pubKey = privKey * generator'
            if (!correspondingPubKey.getAffineXCoord().toBigInteger().equals(pubKey.getW().getAffineX()) ||
                !correspondingPubKey.getAffineYCoord().toBigInteger().equals(pubKey.getW().getAffineY()))
                throw new RuntimeException("Elliptic curve private key does not match public key.");
        }
    }

    public static long getValidFrom(List<Certificate> certs) {
        return certs.stream().map(cert -> ((X509Certificate)cert).getNotBefore().getTime()).max(Long::compare).get();
    }

    public static long getMinimumValidity(List<Certificate> certs) {
        return certs.stream().map(cert -> ((X509Certificate)cert).getNotAfter().getTime()).min(Long::compare).get();
    }

    @Override
    public void stop() {
        // do nothing
    }

    /**
     * @return whether this context has a working certificate and key
     */
    public abstract boolean hasKeyAndCertificate();
    public abstract long getValidFrom();
    public abstract long getValidUntil();
    public abstract String getPrometheusContextTypeName();
}
