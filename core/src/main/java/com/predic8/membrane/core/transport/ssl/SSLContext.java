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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    protected static Method getApplicationProtocols, setApplicationProtocols;

    static {
        try {
            getApplicationProtocols = SSLParameters.class.getDeclaredMethod("getApplicationProtocols");
            setApplicationProtocols = SSLParameters.class.getDeclaredMethod("setApplicationProtocols", String[].class);
        } catch (NoSuchMethodException e) {
        }
    }

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
                    log.warn("Cipher " + cipher + " uses RC4, which is deprecated.");
                if (cipher.contains("_3DES_"))
                    log.warn("Cipher " + cipher + " uses 3DES, which is deprecated.");
            }
        } else {
            // use all default ciphers except those using RC4
            String supportedCiphers[] = sslc.getSocketFactory().getDefaultCipherSuites();
            ArrayList<String> ciphers = new ArrayList<>(supportedCiphers.length);
            for (String cipher : supportedCiphers)
                if (!cipher.contains("_RC4_") && !cipher.contains("_3DES_"))
                    ciphers.add(cipher);
            sortCiphers(ciphers);
            this.ciphers = ciphers.toArray(new String[ciphers.size()]);
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
            String[] protocols = serviceSocket.getEnabledProtocols();
            Set<String> set = new HashSet<>();
            for (String protocol : protocols) {
                if (protocol.equals("SSLv3") || protocol.equals("SSLv2Hello")) {
                    continue;
                }
                set.add(protocol);
            }
            serviceSocket.setEnabledProtocols(set.toArray(new String[0]));
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

    private void sortCiphers(ArrayList<String> ciphers) {
        ArrayList<SSLContext.CipherInfo> cipherInfos = new ArrayList<>(ciphers.size());

        for (String cipher : ciphers)
            cipherInfos.add(new SSLContext.CipherInfo(cipher));

        cipherInfos.sort((cipher1, cipher2) -> cipher2.points - cipher1.points);

        for (int i = 0; i < ciphers.size(); i++)
            ciphers.set(i, cipherInfos.get(i).cipher);
    }

    public String constructHostNamePattern() {
        StringBuilder sb = null;
        List<String> dnsNames = getDnsNames();
        if (dnsNames == null)
            throw new RuntimeException("Could not extract DNS names from the first key's certificate in " + getLocation());
        for (String dnsName : dnsNames) {
            if (sb == null)
                sb = new StringBuilder();
            else
                sb.append(" ");
            sb.append(dnsName);
        }
        if (sb == null) {
            log.warn("Could not retrieve DNS hostname for certificate, using '*': " + getLocation());
            return "*";
        }
        return sb.toString();
    }

    private static class CipherInfo {
        public final String cipher;
        public final int points;

        public CipherInfo(String cipher) {
            this.cipher = cipher;
            int points = 0;
            if (supportsPFS(cipher))
                points = 100;
            points += getAESStrength(cipher) * 5;
            points += getSHAStrength(cipher) * 2;
            points += getChaChaPoly1305Strength(cipher) * 25;
            if (supportsAESGCM(cipher))
                points += 15;
            if (!supportsAESCBC(cipher))
                points += 150;

            this.points = points;
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

        private boolean supportsPFS(String cipher2) {
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
                sb.append("Cert " + String.format("%2d", i) + ": Subject: " + ((X509Certificate)certs.get(i)).getSubjectX500Principal().toString() + "\n");
                sb.append("         Issuer: " + ((X509Certificate)certs.get(i)).getIssuerX500Principal().toString() + "\n");
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
        if (setApplicationProtocols == null || getApplicationProtocols == null)
            return null;
        try {
            return (String[]) getApplicationProtocols.invoke(((SSLSocket) socket).getSSLParameters(), new Object[0]);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected void checkKeyMatchesCert(Key key, List<Certificate> certs) {
        if (key instanceof RSAPrivateCrtKey && certs.get(0).getPublicKey() instanceof RSAPublicKey) {
            RSAPrivateCrtKey privkey = (RSAPrivateCrtKey)key;
            RSAPublicKey pubkey = (RSAPublicKey) certs.get(0).getPublicKey();
            if (!(privkey.getModulus().equals(pubkey.getModulus()) && privkey.getPublicExponent().equals(pubkey.getPublicExponent())))
                throw new RuntimeException("Certificate does not fit to key: " + getLocation());
        }

        if (key instanceof ECPrivateKey && certs.get(0).getPublicKey() instanceof ECPublicKey) {
            ECPrivateKey privkey = (ECPrivateKey) key;
            ECPublicKey pubkey = (ECPublicKey) certs.get(0).getPublicKey();

            if (pubkey.getParams().getCurve().getField() instanceof ECFieldFp) {
                ECFieldFp pubfield = (ECFieldFp) pubkey.getParams().getCurve().getField();
                if (!(privkey.getParams().getCurve().getField() instanceof ECFieldFp))
                    throw new RuntimeException("Elliptic curve differs between private key and public key (ECFieldFp vs ECFieldF2m).");
                ECFieldFp privfield = (ECFieldFp) privkey.getParams().getCurve().getField();
                if (!pubfield.getP().equals(privfield.getP()))
                    throw new RuntimeException("Elliptic curve differs between private key and public key (p).");
            }
            // "pubkey.getParams().getCurve().getField() instanceof ECFieldF2m" is not handled

            if (!pubkey.getParams().getCurve().getA().equals(privkey.getParams().getCurve().getA()))
                throw new RuntimeException("Elliptic curve differs between private key and public key (a).");
            if (!pubkey.getParams().getCurve().getB().equals(privkey.getParams().getCurve().getB()))
                throw new RuntimeException("Elliptic curve differs between private key and public key (b).");
            if (!pubkey.getParams().getGenerator().equals(privkey.getParams().getGenerator())) // = G = (x,y)
                throw new RuntimeException("Elliptic curve differs between private key and public key (generator).");
            if (!pubkey.getParams().getOrder().equals(privkey.getParams().getOrder())) // = n
                throw new RuntimeException("Elliptic curve differs between private key and public key (order).");
            if (pubkey.getParams().getCofactor() != privkey.getParams().getCofactor()) // = h
                throw new RuntimeException("Elliptic curve differs between private key and public key (cofactor).");

            ECMultiplier ecMultiplier = new FixedPointCombMultiplier();

            ECPoint correspondingPubKey = ecMultiplier.multiply(((BCECPublicKey) pubkey).getParameters().getG(), privkey.getS()).normalize();

            // check 'pubKey = privKey * generator'
            if (!correspondingPubKey.getAffineXCoord().toBigInteger().equals(pubkey.getW().getAffineX()) ||
                !correspondingPubKey.getAffineYCoord().toBigInteger().equals(pubkey.getW().getAffineY()))
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
