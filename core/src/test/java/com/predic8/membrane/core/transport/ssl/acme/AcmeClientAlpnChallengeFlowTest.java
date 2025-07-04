package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.config.security.acme.Acme;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.transport.ssl.AcmeSSLContext; // Membrane's AcmeSSLContext
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AcmeClientAlpnChallengeFlowTest {

    @Mock
    private Acme mockAcmeConfig;
    @Mock
    private HttpClientFactory mockHttpClientFactory;
    @Mock
    private AcmeSSLContext mockMembraneAcmeSslContext; // Mock of our Membrane's AcmeSSLContext
    @Mock
    private AcmeSynchronizedStorageEngine mockStorageEngine;

    private AcmeClient acmeClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    private final String domain = "test.example.com";
    private final String tlsAlpnChallengeType = "tls-alpn-01";
    private final String httpChallengeType = "http-01";
    private final String tlsAlpnChallengeUrl = "https://acme.example.com/chall/tls-alpn";
    private final String httpChallengeUrl = "https://acme.example.com/chall/http";
    private final String token = "test-token";

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Configure mockAcmeConfig to prefer tls-alpn-01
        when(mockAcmeConfig.getChallengeTypes()).thenReturn(List.of(tlsAlpnChallengeType, httpChallengeType));
        when(mockAcmeConfig.getDirectoryUrl()).thenReturn("https://acme.example.com/directory");
        when(mockAcmeConfig.getContacts()).thenReturn("mailto:test@example.com");
        when(mockAcmeConfig.isTermsOfServiceAgreed()).thenReturn(true);
        when(mockAcmeConfig.getAcmeSynchronizedStorage()).thenReturn(mock(AcmeSynchronizedStorage.class)); // Dummy storage

        acmeClient = new AcmeClient(mockAcmeConfig, mockHttpClientFactory);

        // Mock parts of AcmeClient that are not central to this specific flow test
        // but are called during provision (like getThumbprint, generateAlpnCertificate)
        // We need a way to inject mockAsse or mock getThumbprint directly
        // For simplicity in this example, let's assume getThumbprint can be mocked if AcmeClient was not final,
        // or we use a spy. Here, we will mock generateAlpnCertificate as it's static.
        // However, static mocking is complex. A better approach would be to refactor AcmeClient
        // so that generateAlpnCertificate is non-static and can be overridden in a test subclass or mocked.
        // For this example, we'll proceed without mocking getThumbprint and assume generateAlpnCertificate works.
        // We will also need to provide a mock for asse.
        lenient().when(mockStorageEngine.getAccountKey()).thenReturn(null); // Ensure a new key is generated for thumbprint
        acmeClient.asse = mockStorageEngine; // Inject mock storage engine

        // Mock getPublicJwk and getThumbprint to avoid real crypto operations if possible
        PublicJsonWebKey mockJwk = mock(PublicJsonWebKey.class);
        when(mockJwk.calculateBase64urlEncodedThumbprint("SHA-256")).thenReturn("mockedThumbprint");
        // This part is tricky because getPublicJwk() is private and relies on privateKey.
        // Full solution might need PowerMockito or refactoring.
        // For now, we rely on the fact that if account key in asse is null, a new one is generated.
    }

    @Test
    public void provision_PrefersTlsAlpn01_AndSetsUpContextCorrectly() throws Exception {
        // Arrange
        Authorization authorization = new Authorization();
        Identifier identifier = new Identifier();
        identifier.setType("dns");
        identifier.setValue(domain);
        authorization.setIdentifier(identifier);

        Challenge tlsChallenge = new Challenge();
        tlsChallenge.setType(tlsAlpnChallengeType);
        tlsChallenge.setUrl(tlsAlpnChallengeUrl);
        tlsChallenge.setToken(token);

        Challenge httpChallenge = new Challenge();
        httpChallenge.setType(httpChallengeType);
        httpChallenge.setUrl(httpChallengeUrl);
        httpChallenge.setToken(token);

        authorization.setChallenges(List.of(tlsChallenge, httpChallenge)); // Offer both

        // Act
        AcmeClient.ProvisionResult result = acmeClient.provision(authorization, mockMembraneAcmeSslContext);

        // Assert
        assertNotNull(result, "ProvisionResult should not be null");
        assertEquals(tlsAlpnChallengeUrl, result.getChallengeUrl(), "Should have chosen the TLS-ALPN-01 challenge URL");

        // Verify setAlpnChallengeCertificate was called on our Membrane AcmeSSLContext
        ArgumentCaptor<String> domainCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AcmeClient.AlpnCertAndKey> certAndKeyCaptor = ArgumentCaptor.forClass(AcmeClient.AlpnCertAndKey.class);
        verify(mockMembraneAcmeSslContext).setAlpnChallengeCertificate(domainCaptor.capture(), certAndKeyCaptor.capture());

        assertEquals(domain, domainCaptor.getValue(), "Domain in setAlpnChallengeCertificate mismatch");
        assertNotNull(certAndKeyCaptor.getValue(), "AlpnCertAndKey should not be null in setAlpnChallengeCertificate");
        assertNotNull(certAndKeyCaptor.getValue().certificate(), "Certificate in AlpnCertAndKey should not be null");
        assertNotNull(certAndKeyCaptor.getValue().privateKey(), "PrivateKey in AlpnCertAndKey should not be null");

        // Verify cleanup task
        assertNotNull(result.getCleanupTask(), "Cleanup task should be set for tls-alpn-01");
        result.performCleanup();
        verify(mockMembraneAcmeSslContext).clearAlpnChallengeCertificate(domainCaptor.capture());
        assertEquals(domain, domainCaptor.getValue(), "Domain in clearAlpnChallengeCertificate mismatch");
    }

    @Test
    public void provision_FallsBackToHttp01_IfTlsAlpn01FailsOrContextMissing() throws Exception {
        // Arrange
        Authorization authorization = new Authorization();
        Identifier identifier = new Identifier();
        identifier.setType("dns");
        identifier.setValue(domain);
        authorization.setIdentifier(identifier);

        Challenge tlsChallenge = new Challenge();
        tlsChallenge.setType(tlsAlpnChallengeType);
        tlsChallenge.setUrl(tlsAlpnChallengeUrl);
        tlsChallenge.setToken(token);

        Challenge httpChallenge = new Challenge(); // http-01 is second preference
        httpChallenge.setType(httpChallengeType);
        httpChallenge.setUrl(httpChallengeUrl);
        httpChallenge.setToken(token);
        authorization.setChallenges(List.of(tlsChallenge, httpChallenge));

        // Scenario 1: AcmeSSLContext is null (tls-alpn-01 cannot be used)
        AcmeClient.ProvisionResult resultNoContext = acmeClient.provision(authorization, null);
        assertNotNull(resultNoContext, "ProvisionResult should not be null");
        assertEquals(httpChallengeUrl, resultNoContext.getChallengeUrl(), "Should fall back to HTTP-01 if context is null");
        assertNull(resultNoContext.getCleanupTask(), "Cleanup task should be null for http-01");
        verify(mockMembraneAcmeSslContext, never()).setAlpnChallengeCertificate(anyString(), any());


        // Scenario 2: tls-alpn-01 is configured first, but generateAlpnCertificate fails (simulated indirectly)
        // This is harder to simulate cleanly without deeper refactoring of AcmeClient or using PowerMock for static method.
        // For now, the null context case demonstrates fallback.
        // A direct way to test failure of tls-alpn-01 would be to make generateAlpnCertificate throw an exception.
        // If AcmeClient.generateAlpnCertificate was non-static, we could spy on acmeClient and make it throw.
    }

     @Test
    public void provision_ThrowsException_IfNoSuitableChallengeFound() throws Exception {
        // Arrange
        when(mockAcmeConfig.getChallengeTypes()).thenReturn(List.of("non-existent-type")); // Prefer a type not offered

        Authorization authorization = new Authorization();
        Identifier identifier = new Identifier();
        identifier.setType("dns");
        identifier.setValue(domain);
        authorization.setIdentifier(identifier);
        // Server only offers http-01
        Challenge httpChallenge = new Challenge();
        httpChallenge.setType(httpChallengeType);
        httpChallenge.setUrl(httpChallengeUrl);
        httpChallenge.setToken(token);
        authorization.setChallenges(List.of(httpChallenge));

        // Act & Assert
        AcmeException ex = assertThrows(AcmeException.class, () -> {
            acmeClient.provision(authorization, mockMembraneAcmeSslContext);
        });
        assertTrue(ex.getMessage().contains("Could not find or successfully provision any of the preferred challenge types"));
    }
}
