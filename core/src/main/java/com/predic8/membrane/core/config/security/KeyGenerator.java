package com.predic8.membrane.core.config.security;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

/**
 * @description Experimental.
 * <p>Allows to dynamically generate keys/certificates for arbitrary domain names on the fly, signed by a specified
 * root CA key.</p>
 * <p>This is an alternative for {@link Key} and {@link KeyStore}.</p>
 */
@MCElement(name="keyGenerator")
public class KeyGenerator {

    private Key key;

    public Key getKey() {
        return key;
    }

    @MCChildElement
    public void setKey(Key key) {
        this.key = key;
    }
}
