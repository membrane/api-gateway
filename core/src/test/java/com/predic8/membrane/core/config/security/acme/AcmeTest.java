package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.core.transport.ssl.acme.Challenge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class AcmeTest {

    @Test
    public void testDefaultChallengeTypes() {
        Acme acme = new Acme();
        List<String> defaultTypes = acme.getChallengeTypes();
        assertNotNull(defaultTypes);
        assertEquals(1, defaultTypes.size());
        assertEquals(Challenge.TYPE_HTTP_01, defaultTypes.get(0));
    }

    @Test
    public void testSetNullChallengeTypes() {
        Acme acme = new Acme();
        acme.setChallengeTypes(null);
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(1, types.size());
        assertEquals(Challenge.TYPE_HTTP_01, types.get(0)); // Should return default
    }

    @Test
    public void testSetEmptyChallengeTypes() {
        Acme acme = new Acme();
        acme.setChallengeTypes("");
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(1, types.size());
        assertEquals(Challenge.TYPE_HTTP_01, types.get(0)); // Should return default

        acme.setChallengeTypes("   "); // Whitespace only
        types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(1, types.size());
        assertEquals(Challenge.TYPE_HTTP_01, types.get(0)); // Should return default
    }

    @Test
    public void testSetSingleChallengeType() {
        Acme acme = new Acme();
        acme.setChallengeTypes(Challenge.TYPE_TLS_ALPN_01);
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(1, types.size());
        assertEquals(Challenge.TYPE_TLS_ALPN_01, types.get(0));
    }

    @Test
    public void testSetMultipleChallengeTypes() {
        Acme acme = new Acme();
        String typeString = String.join(",", Challenge.TYPE_TLS_ALPN_01, Challenge.TYPE_HTTP_01, Challenge.TYPE_DNS_01);
        acme.setChallengeTypes(typeString);
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(3, types.size());
        assertEquals(Challenge.TYPE_TLS_ALPN_01, types.get(0));
        assertEquals(Challenge.TYPE_HTTP_01, types.get(1));
        assertEquals(Challenge.TYPE_DNS_01, types.get(2));
    }

    @Test
    public void testSetMultipleChallengeTypesWithExtraSpaces() {
        Acme acme = new Acme();
        String typeString = "  " + Challenge.TYPE_TLS_ALPN_01 + " , " + Challenge.TYPE_HTTP_01 + "  ,  " + Challenge.TYPE_DNS_01 + " ";
        acme.setChallengeTypes(typeString);
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(3, types.size());
        assertEquals(Challenge.TYPE_TLS_ALPN_01, types.get(0));
        assertEquals(Challenge.TYPE_HTTP_01, types.get(1));
        assertEquals(Challenge.TYPE_DNS_01, types.get(2));
    }

    @Test
    public void testSetChallengeTypesWithEmptySegments() {
        Acme acme = new Acme();
        // Empty segments should be filtered out
        String typeString = Challenge.TYPE_TLS_ALPN_01 + ",,,  " + Challenge.TYPE_HTTP_01 + ",";
        acme.setChallengeTypes(typeString);
        List<String> types = acme.getChallengeTypes();
        assertNotNull(types);
        assertEquals(2, types.size());
        assertEquals(Challenge.TYPE_TLS_ALPN_01, types.get(0));
        assertEquals(Challenge.TYPE_HTTP_01, types.get(1));
    }
}
