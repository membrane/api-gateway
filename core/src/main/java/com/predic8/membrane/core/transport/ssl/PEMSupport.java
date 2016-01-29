/* Copyright 2015 predic8 GmbH, www.predic8.com

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

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public abstract class PEMSupport {

    public abstract X509Certificate parseCertificate(String pemBlock) throws IOException;
    public abstract Key getPrivateKey(String content) throws IOException;
    public abstract Object parseKey(String content) throws IOException;

    private static PEMSupport instance;

    public static synchronized PEMSupport getInstance() {
        if (instance == null)
            try {
                instance = new PEMSupportImpl();
            } catch (NoClassDefFoundError e) {
                throw new RuntimeException("Bouncycastle support classes not found. Please download http://central.maven.org/maven2/org/bouncycastle/bcpkix-jdk15on/1.54/bcpkix-jdk15on-1.54.jar and put it into the 'lib' directory.");
            }
        return instance;
    }

    private static class PEMSupportImpl extends PEMSupport {

        public PEMSupportImpl() {
            Security.addProvider(new BouncyCastleProvider());
        }

        private String cleanupPEM(String pemBlock) {
            String lines[] = pemBlock.split("\r?\n");
            StringBuilder block = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].replaceAll("^\\s+", "");
                if (l.length() > 0) {
                    block.append(l);
                    block.append("\n");
                }
            }
            return block.toString();
        }

        public X509Certificate parseCertificate(String pemBlock) throws IOException {
            PEMParser p2 = new PEMParser(new StringReader(cleanupPEM(pemBlock)));
            Object o2 = p2.readObject();
            if (o2 == null)
                throw new InvalidParameterException("Could not read certificate. Expected the certificate to begin with '-----BEGIN CERTIFICATE-----'.");
            if (!(o2 instanceof X509CertificateHolder))
                throw new InvalidParameterException("Expected X509CertificateHolder, got " + o2.getClass().getName());

            JcaX509CertificateConverter certconv = new JcaX509CertificateConverter().setProvider("BC");
            try {
                return certconv.getCertificate((X509CertificateHolder) o2);
            } catch (CertificateException e) {
                throw new IOException(e);
            }
        }

        public Key getPrivateKey(String pemBlock) throws IOException {
            PEMParser p = new PEMParser(new StringReader(cleanupPEM(pemBlock)));
            Object o = p.readObject();
            if (o == null)
                throw new InvalidParameterException("Could not read certificate. Expected the certificate to begin with '-----BEGIN CERTIFICATE-----'.");
            if (o instanceof PEMKeyPair) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                return converter.getPrivateKey(((PEMKeyPair) o).getPrivateKeyInfo());
            }
            if (o instanceof Key)
                return (Key) o;
            if (o instanceof KeyPair)
                return ((KeyPair) o).getPrivate();
            throw new InvalidParameterException("Expected KeyPair or Key.");
        }

        public Object parseKey(String pemBlock) throws IOException {
            PEMParser p = new PEMParser(new StringReader(cleanupPEM(pemBlock)));
            Object o = p.readObject();
            if (o == null)
                throw new InvalidParameterException("Could not read certificate. Expected the certificate to begin with '-----BEGIN CERTIFICATE-----'.");
            if (o instanceof PEMKeyPair) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                return converter.getKeyPair((PEMKeyPair) o);
            }
            if (o instanceof Key)
                return o;
            if (o instanceof KeyPair)
                return o;
            if (o instanceof PrivateKeyInfo) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                return converter.getPrivateKey((PrivateKeyInfo)o);
            }
            throw new InvalidParameterException("Expected KeyPair or Key, got " + o.getClass().getName());
        }
    }
}
