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

import com.google.common.base.*;
import com.google.common.collect.Sets;
import com.predic8.membrane.core.config.security.SSLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

public abstract class SSLContext implements SSLProvider {
    private static final Logger log = LoggerFactory.getLogger(SSLContext.class.getName());

    protected String[] ciphers;
    protected String[] protocols;
    protected boolean wantClientAuth, needClientAuth;
    protected String endpointIdentificationAlgorithm;

    public void init(SSLParser sslParser, javax.net.ssl.SSLContext sslc) {
        if (sslParser.getCiphers() != null) {
            ciphers = sslParser.getCiphers().split(",");
            Set<String> supportedCiphers = Sets.newHashSet(sslc.getSocketFactory().getSupportedCipherSuites());
            for (String cipher : ciphers) {
                if (!supportedCiphers.contains(cipher))
                    throw new InvalidParameterException("Unknown cipher " + cipher);
                if (cipher.contains("_RC4_"))
                    log.warn("Cipher " + cipher + " uses RC4, which is deprecated.");
            }
        } else {
            // use all default ciphers except those using RC4
            String supportedCiphers[] = sslc.getSocketFactory().getDefaultCipherSuites();
            ArrayList<String> ciphers = new ArrayList<String>(supportedCiphers.length);
            for (String cipher : supportedCiphers)
                if (!cipher.contains("_RC4_"))
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
            Set<String> set = new HashSet<String>();
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
        ArrayList<SSLContext.CipherInfo> cipherInfos = new ArrayList<SSLContext.CipherInfo>(ciphers.size());

        for (String cipher : ciphers)
            cipherInfos.add(new SSLContext.CipherInfo(cipher));

        Collections.sort(cipherInfos, new Comparator<SSLContext.CipherInfo>() {
            @Override
            public int compare(SSLContext.CipherInfo cipher1, SSLContext.CipherInfo cipher2) {
                return cipher2.points - cipher1.points;
            }
        });

        for (int i = 0; i < ciphers.size(); i++)
            ciphers.set(i, cipherInfos.get(i).cipher);
    }

    private static class CipherInfo {
        public final String cipher;
        public final int points;

        public CipherInfo(String cipher) {
            this.cipher = cipher;
            int points = 0;
            if (supportsPFS(cipher))
                points = 1;

            this.points = points;
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

}
