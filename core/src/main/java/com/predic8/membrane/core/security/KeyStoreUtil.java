package com.predic8.membrane.core.security;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.Optional;

public class KeyStoreUtil {
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