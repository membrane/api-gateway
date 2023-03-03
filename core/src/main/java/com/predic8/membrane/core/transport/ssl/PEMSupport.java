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
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jose4j.base64url.Base64;

import java.io.IOException;
import java.io.StringReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class PEMSupport {

    public abstract X509Certificate parseCertificate(String pemBlock) throws IOException;
    public abstract List<? extends Certificate> parseCertificates(String certificates) throws IOException;
    public abstract Key getPrivateKey(String content) throws IOException;
    public abstract Object parseKey(String content) throws IOException;

    private static PEMSupport instance;

    public static synchronized PEMSupport getInstance() {
        if (instance == null)
            try {
                instance = new PEMSupportImpl();
            } catch (NoClassDefFoundError e) {
                throw new RuntimeException("Bouncycastle support classes not found. Please download http://central.maven.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.71/bcpkix-jdk18on-1.71.jar and http://central.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.71/bcprov-jdk18on-1.71.jar and put them into the 'lib' directory.");
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
            for (String line : lines) {
                String l = line.replaceAll("^\\s+", "");
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
        public List<X509Certificate> parseCertificates(String pemBlock) throws IOException {
            List<X509Certificate> res = new ArrayList<>();
            PEMParser p2 = new PEMParser(new StringReader(cleanupPEM(pemBlock)));
            JcaX509CertificateConverter certconv = new JcaX509CertificateConverter().setProvider("BC");
            while(true) {
                Object o2 = p2.readObject();
                if (o2 == null)
                    break;
                if (!(o2 instanceof X509CertificateHolder))
                    throw new InvalidParameterException("Expected X509CertificateHolder, got " + o2.getClass().getName());

                try {
                    res.add(certconv.getCertificate((X509CertificateHolder) o2));
                } catch (CertificateException e) {
                    throw new IOException(e);
                }
            }
            if (res.size() == 0)
                throw new InvalidParameterException("Could not read certificate. Expected the certificate to begin with '-----BEGIN CERTIFICATE-----'.");
            return res;
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
            if (o instanceof X9ECParameters) {
                o = p.readObject();
            }
            if (o instanceof PEMKeyPair) {
                if (((PEMKeyPair)o).getPublicKeyInfo() == null) {
                    // bouncycastle has failed to dereference well-known curve OIDs, e.g. '1.3.132.0.34', to fill
                    // the algorithm parameters
                    try {
                        Pattern p1 = Pattern.compile("^.*-----BEGIN EC PRIVATE KEY-----\r?\n", Pattern.DOTALL);
                        Pattern p2 = Pattern.compile("-----END EC PRIVATE KEY-----\r?\n?.*", Pattern.DOTALL);
                        String s = p2.matcher(p1.matcher(pemBlock).replaceAll("")).replaceAll("");
                        return KeyFactory.getInstance("EC", "SunEC")
                                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(s)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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
