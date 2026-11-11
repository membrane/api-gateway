/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.as2;

import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.security.KeyStoreUtil;
import com.predic8.membrane.core.transport.ssl.PEMSupport;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Loads and checks the AS2 IDs, keys and certificates of the AS2 interceptors. The messages name the interceptor,
 * e.g. as2Receiver.
 */
final class As2Keys {

    /**
     * RFC 4130, section 6.2
     */
    static final int MAX_AS2_ID_LENGTH = 128;

    record OwnKey(PrivateKey privateKey, X509Certificate certificate) {

        boolean isRsa() {
            return privateKey.getAlgorithm().equals("RSA");
        }
    }

    private final String element;
    private final ResolverMap resolverMap;
    private final String baseLocation;

    /**
     * @param element      Name of the interceptor, used in messages
     * @param resolverMap  Resolves the locations of keys and certificates
     * @param baseLocation Relative locations are resolved against it
     */
    As2Keys(final String element, final ResolverMap resolverMap, final String baseLocation) {
        this.element = element;
        this.resolverMap = resolverMap;
        this.baseLocation = baseLocation;
    }

    /**
     * Same rules as the AS2 library: 1 to 128 printable ASCII characters. Leading or trailing spaces would never
     * match, because AS2 IDs in headers are trimmed.
     */
    static void checkAs2Id(final String what, final String id) {
        if (id == null || id.isEmpty())
            throw new ConfigurationException("%s is required.".formatted(what));
        if (id.length() > MAX_AS2_ID_LENGTH)
            throw new ConfigurationException("%s must not be longer than %d characters.".formatted(what, MAX_AS2_ID_LENGTH));
        if (!id.chars().allMatch(c -> c >= 32 && c <= 126))
            throw new ConfigurationException("%s must consist of printable ASCII characters.".formatted(what));
        if (!id.strip().equals(id))
            throw new ConfigurationException("%s must not start or end with a space.".formatted(what));
    }

    /**
     * Loads the own key from either the keystore or the PEM key, and checks that it is an RSA or EC key and that the
     * certificate belongs to it.
     */
    OwnKey loadOwnKey(final KeyStore keyStore, final Key key) {
        // Not @Required: a child set through $ref does not count for @Required (#3597).
        if ((keyStore == null) == (key == null))
            throw new ConfigurationException("%s needs either a keystore or a key.".formatted(element));
        final var ownKey = keyStore != null ? loadFromKeyStore(keyStore) : loadFromKey(key);
        checkKeyMatchesCertificate(ownKey, signatureAlgorithm(ownKey));
        return ownKey;
    }

    private OwnKey loadFromKeyStore(final KeyStore keyStore) {
        try {
            final var keyPassword = keyPassword(keyStore);
            final var ks = StaticSSLContext.openKeyStore(keyStore, keyPassword, resolverMap, baseLocation);
            final var alias = keyStore.getKeyAlias() != null
                    ? KeyStoreUtil.aliasOrThrow(ks, keyStore.getKeyAlias())
                    : KeyStoreUtil.firstAliasOrThrow(ks);
            if (!(ks.getKey(alias, keyPassword) instanceof PrivateKey privateKey))
                throw new ConfigurationException("Keystore alias \"%s\" of %s holds no private key.".formatted(alias, element));
            if (!(ks.getCertificate(alias) instanceof X509Certificate certificate))
                throw new ConfigurationException("Keystore alias \"%s\" of %s holds no X.509 certificate.".formatted(alias, element));
            return new OwnKey(privateKey, certificate);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the key of %s from the keystore: %s".formatted(element, e.getMessage()), e);
        }
    }

    /**
     * Like wsSecurity: the key of a PKCS12 keystore usually has the password of the store.
     */
    private static char[] keyPassword(final KeyStore keyStore) {
        if (keyStore.getKeyPassword() != null)
            return keyStore.getKeyPassword().toCharArray();
        if (keyStore.getPassword() != null)
            return keyStore.getPassword().toCharArray();
        return "changeit".toCharArray();
    }

    private OwnKey loadFromKey(final Key key) {
        if (key.getPrivate() == null)
            throw new ConfigurationException("The key of %s needs a private key.".formatted(element));
        if (key.getCertificates().isEmpty())
            throw new ConfigurationException("The key of %s needs a certificate.".formatted(element));
        try {
            final var parsed = PEMSupport.getInstance().parseKey(key.getPrivate().get(resolverMap, baseLocation));
            final var privateKey = parsed instanceof KeyPair pair ? pair.getPrivate() : (PrivateKey) parsed;
            final var certificate = PEMSupport.getInstance().parseCertificate(
                    key.getCertificates().getFirst().get(resolverMap, baseLocation));
            return new OwnKey(privateKey, certificate);
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the key of %s: %s".formatted(element, e.getMessage()), e);
        }
    }

    /**
     * The AS2 library signs with RSA or EC keys.
     */
    private String signatureAlgorithm(final OwnKey ownKey) {
        final var algorithm = ownKey.privateKey().getAlgorithm();
        return switch (algorithm) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> throw new ConfigurationException("The key of %s is a %s key. AS2 needs an RSA or EC key.".formatted(element, algorithm));
        };
    }

    private void checkKeyMatchesCertificate(final OwnKey ownKey, final String signatureAlgorithm) {
        final var data = "AS2 key check".getBytes(US_ASCII);
        try {
            final var signature = Signature.getInstance(signatureAlgorithm);
            signature.initSign(ownKey.privateKey());
            signature.update(data);
            final var signed = signature.sign();
            signature.initVerify(ownKey.certificate().getPublicKey());
            signature.update(data);
            if (signature.verify(signed))
                return;
        } catch (GeneralSecurityException e) {
            // e.g. an RSA certificate for an EC key
            throw new ConfigurationException("The certificate of %s does not belong to its private key.".formatted(element), e);
        }
        throw new ConfigurationException("The certificate of %s does not belong to its private key.".formatted(element));
    }

    /**
     * Checks the AS2 ID of the partner and loads its PEM certificate.
     */
    X509Certificate loadPartnerCertificate(final As2Partner partner) {
        checkAs2Id("as2Id of a partner of %s".formatted(element), partner.getAs2Id());
        if (partner.getCertificate() == null)
            throw new ConfigurationException("Partner %s of %s needs a certificate.".formatted(partner.getAs2Id(), element));
        return loadCertificate(partner.getAs2Id(), partner.getCertificate());
    }

    private X509Certificate loadCertificate(final String partnerAs2Id, final Certificate certificate) {
        try {
            return PEMSupport.getInstance().parseCertificate(certificate.get(resolverMap, baseLocation));
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the certificate of partner %s of %s: %s".formatted(partnerAs2Id, element, e.getMessage()), e);
        }
    }

    /**
     * The AS2 library does not check the validity period, so an expired certificate keeps working. Partners might
     * reject it, though.
     *
     * @param log Logger of the interceptor, so that the warning is logged under its name
     */
    static void warnIfExpired(final Logger log, final String what, final X509Certificate certificate) {
        if (certificate.getNotAfter().toInstant().isBefore(Instant.now()))
            log.warn("{} expired on {}.", what, certificate.getNotAfter().toInstant());
    }
}
