package com.predic8.membrane.core.interceptor.oauth2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2UtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"openid", "openid foo", "foo openid", "foo openid bar"})
    void isOpenIdScope(String scope) {
        assertTrue(OAuth2Util.isOpenIdScope(scope));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "foo", "foo bar", "mopenid"})
    void isNotOpenIdScope(String scope) {
        assertFalse(OAuth2Util.isOpenIdScope(scope));
    }

}