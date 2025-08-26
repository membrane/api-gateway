package com.predic8.membrane.core.transport.ssl.acme;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AcmeClientTest {

    @BeforeAll
    public static void setup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testGenerateAlpnCertificate() throws Exception {
        String domainName = "example.com";
        String keyAuthorization = "testKeyAuthStringForAlpn";

        AcmeClient.AlpnCertAndKey certAndKey = AcmeClient.generateAlpnCertificate(domainName, keyAuthorization);

        assertNotNull(certAndKey, "AlpnCertAndKey should not be null");
        assertNotNull(certAndKey.certificate(), "Certificate should not be null");
        assertNotNull(certAndKey.privateKey(), "Private key should not be null");

        X509Certificate cert = certAndKey.certificate();

        // Verify SubjectDN and IssuerDN
        assertEquals("CN=" + domainName, cert.getSubjectX500Principal().getName(), "SubjectDN mismatch");
        assertEquals("CN=" + domainName, cert.getIssuerX500Principal().getName(), "IssuerDN mismatch (self-signed)");

        // Verify Validity (simple check, more precise would require date mocking or wider range)
        assertNotNull(cert.getNotBefore(), "NotBefore date is null");
        assertNotNull(cert.getNotAfter(), "NotAfter date is null");
        assertTrue(cert.getNotAfter().getTime() > cert.getNotBefore().getTime(), "NotAfter should be after NotBefore");
        // Check if validity is roughly 1 day (e.g. between 23 hours and 25 hours)
        long validityPeriodMillis = cert.getNotAfter().getTime() - cert.getNotBefore().getTime();
        long oneDayMillis = 24 * 60 * 60 * 1000;
        assertTrue(validityPeriodMillis > oneDayMillis - (60*60*1000) && validityPeriodMillis < oneDayMillis + (60*60*1000),
                "Validity period is not approximately 1 day");


        // Verify SubjectAlternativeName (SAN)
        Collection<List<?>> sanEntries = cert.getSubjectAlternativeNames();
        assertNotNull(sanEntries, "SAN entries should not be null");
        boolean foundSan = false;
        for (List<?> entry : sanEntries) {
            if (entry.size() == 2 && (Integer) entry.get(0) == GeneralName.dNSName) {
                if (domainName.equals(entry.get(1))) {
                    foundSan = true;
                    break;
                }
            }
        }
        assertTrue(foundSan, "SAN dNSName for " + domainName + " not found");

        // Verify acmeIdentifier extension (OID 1.3.6.1.5.5.7.1.31)
        // OID for pe-acmeIdentifier as defined in RFC 8737
        String acmeIdentifierOidString = "1.3.6.1.5.5.7.1.31";
        byte[] extensionValue = cert.getExtensionValue(acmeIdentifierOidString);
        assertNotNull(extensionValue, "acmeIdentifier extension not found");

        // Check if critical
        assertTrue(cert.getCriticalExtensionOIDs().contains(acmeIdentifierOidString), "acmeIdentifier extension should be critical");

        // Extract the octet string value from the extension
        ASN1InputStream asn1Input = new ASN1InputStream(extensionValue);
        DEROctetString octetString = (DEROctetString) asn1Input.readObject();
        asn1Input.close();
        byte[] actualDigest = octetString.getOctets();

        // Calculate expected SHA-256 digest
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expectedDigest = md.digest(keyAuthorization.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(expectedDigest, actualDigest, "SHA-256 digest of keyAuthorization in acmeIdentifier mismatch");
    }
}
