package com.predic8.membrane.core.transport.ssl.acme;

import com.predic8.membrane.core.config.security.acme.KubernetesStorage;
import com.predic8.membrane.core.kubernetes.client.KubernetesApiException;
import com.predic8.membrane.core.kubernetes.client.KubernetesClient;
import com.predic8.membrane.core.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.google.common.collect.ImmutableMap.of;
import static java.nio.charset.StandardCharsets.UTF_8;

public class AcmeKubernetesStorageEngine implements AcmeSynchronizedStorageEngine {

    private static final Logger LOG = LoggerFactory.getLogger(AcmeKubernetesStorageEngine.class);

    private final KubernetesClient client;
    private final String namespace;
    private final String lease;
    private final String identity = UUID.randomUUID().toString();
    private final SimpleDateFormat sdf, sdf2;
    private final String accountSecret;
    private final String prefix;

    public AcmeKubernetesStorageEngine(KubernetesStorage ks) {
        try {
            client = KubernetesClientBuilder.auto().build();
        } catch (KubernetesClientBuilder.ParsingException e) {
            throw new RuntimeException(e);
        }
        namespace = ks.getNamespace() != null ? ks.getNamespace() : client.getNamespace();
        lease = ks.getMasterLease();
        LOG.info("acme: using identity " + identity + " for master election");

        sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS000'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));

        accountSecret = ks.getAccountSecret();
        prefix = ks.getPrefix() == null ? "" : ks.getPrefix();
    }

    @Override
    public String getAccountKey() {
        return getSecretEntry(accountSecret, "key");
    }


    @Override
    public void setAccountKey(String key) {
        setSecretEntry(accountSecret, "key", key);
    }

    @Override
    public String getAccountURL() {
        return getSecretEntry(accountSecret, "url");
    }

    @Override
    public void setAccountURL(String url) {
        setSecretEntry(accountSecret, "url", url);
    }

    @Override
    public String getAccountContacts() {
        return getSecretEntry(accountSecret, "contacts");
    }

    @Override
    public void setAccountContacts(String contacts) {
        setSecretEntry(accountSecret, "contacts", contacts);
    }

    @Override
    public void setKeyPair(String[] hosts, AcmeKeyPair key) {
        setSecretEntry(prefix + id(hosts), "key-public", key.getPublicKey(), "key-private", key.getPrivateKey());
    }

    @Override
    public String getPublicKey(String[] hosts) {
        return getSecretEntry(prefix + id(hosts), "key-public");
    }

    @Override
    public String getPrivateKey(String[] hosts) {
        return getSecretEntry(prefix + id(hosts), "key-private");
    }

    @Override
    public void setCertChain(String[] hosts, String caChain) {
        setSecretEntry(prefix + id(hosts), "certs", caChain);
    }

    @Override
    public String getCertChain(String[] hosts) {
        return getSecretEntry(prefix + id(hosts), "certs");
    }

    @Override
    public void setToken(String host, String token) {
        setSecretEntry(prefix + host + "-token", "token", token);
    }

    @Override
    public String getToken(String host) {
        return getSecretEntry(prefix + host + "-token", "token");
    }

    @Override
    public String getOAL(String[] hosts) {
        return getSecretEntry(prefix + id(hosts) + "-oal-current", "oal");
    }

    @Override
    public void setOAL(String[] hosts, String oal) {
        setSecretEntry(prefix + id(hosts) + "-oal-current", "oal", oal);
    }

    @Override
    public String getOALError(String[] hosts) {
        return getSecretEntry(prefix + id(hosts) + "-oal-current", "error");
    }

    @Override
    public void setOALError(String[] hosts, String oalError) {
        setSecretEntry(prefix + id(hosts) + "-oal-current", "error", oalError);
    }

    @Override
    public String getOALKey(String[] hosts) {
        return getSecretEntry(prefix + id(hosts) + "-oal-current", "key");
    }

    @Override
    public void setOALKey(String[] hosts, String oalKey) {
        setSecretEntry(prefix + id(hosts) + "-oal-current", "key", oalKey);
    }

    @Override
    public void archiveOAL(String[] hosts) {
        try {
            Map secret = client.read("v1", "Secret", namespace, prefix + id(hosts) + "-oal-current");
            Map metadata = (Map) secret.get("metadata");
            metadata.remove("managedFields");
            metadata.remove("creationTimestamp");
            metadata.remove("uid");
            metadata.remove("resourceVersion");
            metadata.put("name", prefix + id(hosts) + "-oal-" + System.currentTimeMillis());
            client.create(secret);
            client.delete("v1", "Secret", namespace, prefix + id(hosts) + "-oal-current");
        } catch (IOException | KubernetesApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean acquireLease(long durationMillis) {
        String renewTime; // "2022-06-02T05:28:03.186642Z"
        synchronized (sdf) {
            renewTime = sdf.format(new Date(System.currentTimeMillis() + durationMillis));
        }
        Map l = of(
                "apiVersion", "coordination.k8s.io/v1",
                "kind", "Lease",
                "metadata", of(
                    "name", lease,
                    "namespace", namespace),
                "spec", of());
        try {
            client.createAndEdit(l, le -> {
                // check expired or no holder
                Map spec = (Map) le.get("spec");
                if (spec == null) {
                    spec = new HashMap();
                    le.put("spec", spec);
                }
                String oldHolder = (String) spec.get("holderIdentity");
                if (oldHolder != null && !"".equals(oldHolder)) {
                    // check expiry
                    String oldRenew = (String) spec.get("renewTime");
                    if (oldRenew == null || "".equals(oldRenew))
                        throw new LeaseException("holder, but no renew time is set.");
                    try {
                        if (new Date().getTime() < parse(oldRenew).getTime())
                            throw new LeaseException("lease is not expired yet.");
                    } catch (ParseException e) {
                        throw new LeaseException(e);
                    }
                }
                spec.put("holderIdentity", identity);
                spec.put("renewTime", renewTime);
                spec.put("leaseTransitions", longValue(spec.get("leaseTransitions")) + 1);
            });
            return true;
        } catch (LeaseException e) {
            LOG.debug("Could not acquire lease.", e);
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (KubernetesApiException e) {
            LOG.warn("Could not acquire lease.", e);
            return false;
        }
    }

    /**
     * Parse date in the format of {@code sdf}, but Kubernetes-style with microsecond precision.
     */
    private Date parse(String date) throws ParseException {
        int p = date.indexOf('Z');
        if (p > 7) {
            if (Character.isDigit(date.charAt(p-1)) && Character.isDigit(date.charAt(p-2)) && Character.isDigit(date.charAt(p-3)) &&
                    Character.isDigit(date.charAt(p-4)) && Character.isDigit(date.charAt(p-5)) && Character.isDigit(date.charAt(p-6))) {
                date = date.substring(0, p-3) + date.substring(p);
            }
        }
        synchronized (sdf2) {
            return sdf2.parse(date);
        }
    }

    private long longValue(Object l) {
        if (l == null)
            return 0;
        if (l instanceof Long)
            return (Long)l;
        if (l instanceof Integer)
            return (Integer)l;
        if (l instanceof Byte)
            return (Byte)l;
        if (l instanceof Short)
            return (Short)l;
        throw new RuntimeException("Unhandled number type: " + l.getClass().getName());
    }

    @Override
    public boolean prolongLease(long durationMillis) {
        String renewTime; // "2022-06-02T05:28:03.186642Z"
        synchronized (sdf) {
            renewTime = sdf.format(new Date(System.currentTimeMillis() + durationMillis));
        }

        try {
            client.edit("coordination.k8s.io/v1", "Lease", namespace, lease, le -> {
                // check that holder is us
                Map spec = (Map) le.get("spec");
                String oldHolder = (String) spec.get("holderIdentity");
                if (!identity.equals(oldHolder)) {
                    throw new LeaseException("holder is not us.");
                }
                // check expiry
                String oldRenew = (String) spec.get("renewTime");
                try {
                    if (new Date().getTime() > parse(oldRenew).getTime())
                        throw new LeaseException("lease has already expired.");
                } catch (ParseException e) {
                    throw new LeaseException(e);
                }
                spec.put("renewTime", renewTime);
            });
            return true;
        } catch (LeaseException | KubernetesApiException e) {
            LOG.warn("could not prolong lease.", e);
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void releaseLease() {
        try {
            client.edit("coordination.k8s.io/v1", "Lease", namespace, lease, le -> {
                // check that holder is us
                Map spec = (Map) le.get("spec");
                String oldHolder = (String) spec.get("holderIdentity");
                if (!identity.equals(oldHolder)) {
                    throw new LeaseException("holder is not us.");
                }
                spec.put("holderIdentity", "");
                spec.remove("renewTime");
                spec.put("leaseTransitions", longValue(spec.get("leaseTransitions")) + 1);
            });
        } catch (LeaseException | KubernetesApiException e) {
            LOG.warn("could not release lease.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getSecretEntry(String secretName, String secretKey) {
        try {
            String b64 = (String) ((Map) client.read("v1", "Secret", namespace, secretName).get("data")).get(secretKey);
            if (b64 == null)
                return null;
            return new String(Base64.getDecoder().decode(b64), UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (KubernetesApiException e) {
            if (e.getCode() == 404 && "NotFound".equals(e.getReason()))
                return null;
            throw new RuntimeException(e);
        }
    }

    private void setSecretEntry(String secretName, String secretKey, String value) {
        setSecretEntry(secretName, secretKey, value, null, null);
    }

    private void setSecretEntry(String secretName, String secretKey1, String value1, String secretKey2, String value2) {
        String v1B64 = Base64.getEncoder().encodeToString(value1.getBytes(UTF_8));
        String v2B64 = secretKey2 == null ? null : Base64.getEncoder().encodeToString(value2.getBytes(UTF_8));
        Map secret = of("apiVersion", "v1",
                "data", of(),
                "kind", "Secret",
                "metadata", of(
                        "name", secretName,
                        "namespace", namespace),
                "type", "Opaque");
        try {
            client.createAndEdit(secret, m -> {
                Map data = (Map) m.get("data");
                if (data == null) {
                    data = new HashMap();
                    m.put("data", data);
                }

                data.put(secretKey1, v1B64);
                if (secretKey2 != null)
                    data.put(secretKey2, v2B64);
            });
        } catch (IOException | KubernetesApiException e) {
            throw new RuntimeException(e);
        }
    }

    private String id(String[] hosts) {
        int i = Arrays.hashCode(hosts);
        if (i < 0)
            i = Integer.MAX_VALUE + i + 1;
        return hosts[0] + (hosts.length > 1 ? "-" + i : "");
    }

    private static class LeaseException extends RuntimeException {
        public LeaseException(String message) {
            super(message);
        }

        public LeaseException(Throwable cause) {
            super(cause);
        }
    }
}
