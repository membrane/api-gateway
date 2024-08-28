/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.security;

import com.predic8.membrane.core.config.security.Store;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.Optional;

public class KeyStoreUtil {
    /**
     * Generates an SHA-256 digest of the certificate associated with the given alias in the KeyStore.
     *
     * @param ks    The KeyStore containing the certificate.
     * @param alias The alias of the certificate in the KeyStore.
     * @return A String representation of the SHA-256 digest, with bytes separated by colons.
     * @throws CertificateEncodingException If there's an error encoding the certificate.
     * @throws KeyStoreException           If there's an error accessing the KeyStore.
     * @throws NoSuchAlgorithmException    If the SHA-256 algorithm is not available.
     */
    public static @org.jetbrains.annotations.NotNull String getDigest(KeyStore ks, String alias) throws CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException {
        byte[] pkeEnc = ks.getCertificate(alias).getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(pkeEnc);
        byte[] mdbytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mdbytes.length; i++) {
            if (i > 0)
                sb.append(':');
            sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    /**
     * Retrieves and loads a KeyStore based on the provided parameters.
     *
     * @param store The Store object containing information about the keystore.
     * @param resourceResolver The ResolverMap used to resolve the keystore location.
     * @param baseLocation The base location to be combined with the store's location.
     * @param type The type of the KeyStore (e.g., "JKS", "PKCS12").
     * @param password The password used to check the integrity of the keystore.
     * @return A loaded KeyStore instance.
     * @throws KeyStoreException If there's an error accessing the keystore.
     * @throws NoSuchProviderException If the specified provider is not found.
     * @throws IOException If there's an I/O error while loading the keystore.
     * @throws NoSuchAlgorithmException If the algorithm used to check the integrity of the keystore cannot be found.
     * @throws CertificateException If any of the certificates in the keystore could not be loaded.
     */
    public static @NotNull KeyStore getAndLoadKeyStore(Store store, ResolverMap resourceResolver, String baseLocation, String type, char[] password) throws KeyStoreException, NoSuchProviderException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks;
        if (store.getProvider() != null)
            ks = KeyStore.getInstance(type, store.getProvider());
        else
            ks = KeyStore.getInstance(type);
        ks.load(resourceResolver.resolve(ResolverMap.combine(baseLocation, store.getLocation())), password);
        return ks;
    }

    /**
     * Retrieves the first certificate alias from the provided key store.
     *
     * @param ks the key store from which to retrieve the certificate alias
     * @return the first certificate alias as a String
     * @throws KeyStoreException if there is an error accessing the key store
     * @throws RuntimeException  if no certificate is available in the key store
     */
    public static String firstAliasOrThrow(KeyStore ks) throws KeyStoreException {
        String keyAlias;
        Optional<String> alias = getFirstCertAlias(ks);
        if (alias.isPresent()) {
            keyAlias = alias.get();
        } else {
            throw new RuntimeException("No certificate available in key store.");
        }
        return keyAlias;
    }

    /**
     * Returns alias if it was found within the specified key store, else throws.
     *
     * @param ks         the KeyStore which will be queried
     * @param alias the alias of the certificate to be queried
     * @return the alias of the certificate if it exists
     * @throws KeyStoreException if the key store has not been initialized
     * @throws RuntimeException  if the certificate with the specified alias is not present in the key store
     */
    public static String aliasOrThrow(KeyStore ks, String alias) throws KeyStoreException {
        String keyAlias;
        if (!ks.isKeyEntry(alias)) {
            throw new RuntimeException("Certificate of alias " + alias + " not present in key store.");
        } else {
            keyAlias = alias;
        }
        return keyAlias;
    }

    /**
     * Retrieves the first alias of a key entry from the specified KeyStore.
     *
     * @param keystore the key store to search for key entries
     * @return Optional containing the first alias of a key entry, or an empty Optional if none is found
     * @throws KeyStoreException is thrown if key store has not been initialized
     */
    public static Optional<String> getFirstCertAlias(KeyStore keystore) throws KeyStoreException {
        Enumeration<String> aliases = keystore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keystore.isKeyEntry(alias)) {
                return Optional.of(alias);
            }
        }
        return Optional.empty();
    }
}