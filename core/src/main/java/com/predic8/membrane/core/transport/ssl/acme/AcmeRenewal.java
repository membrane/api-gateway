package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.predic8.membrane.core.transport.ssl.PEMSupport;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.transport.ssl.AcmeSSLContext.RENEW_PERIOD;
import static com.predic8.membrane.core.transport.ssl.SSLContext.getMinimumValidity;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_PENDING;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_VALID;
import static com.predic8.membrane.core.transport.ssl.acme.Challenge.*;
import static com.predic8.membrane.core.transport.ssl.acme.Order.*;

public class AcmeRenewal {
    private static final Logger LOG = LoggerFactory.getLogger(AcmeRenewal.class);
    private static final long ERROR_WAIT_MILLISECONDS = 5 * 60 * 1000;
    private static final long LEASE_DURATION_MILLISECONDS = 5 * 60 * 1000;
    private static final long LEASE_RENEW_MILLISECONDS = 4 * 60 * 1000;

    private final AcmeSynchronizedStorageEngine asse;
    private final String[] hosts;
    private final AcmeClient client;
    private final ObjectMapper om;

    public AcmeRenewal(AcmeClient client, String[] hosts) {
        this.client = client;
        asse = client.getAsse();
        this.hosts = hosts;
        om = new ObjectMapper().registerModule(new JodaModule());
    }

    public void doWork() {
        if (!requiresWork())
            return;
        withMasterLease(() -> {
            try {
                tryGetCertificate();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void tryGetCertificate() throws Exception {
        client.loadDirectory();

        verifyAccountContact();
        if (getAccountURL() == null) {
            client.ensureAccountKeyExists();
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): storing account URL");
            setAccountURL(client.createAccount());
        }
        if (isOALExpiredOrError()) {
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): archiving OAL");
            client.getAsse().archiveOAL(hosts);
        }
        if (getOAL() == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): creating OAL");
            setOAL(client.createOrder(getAccountURL(), Arrays.asList(hosts)));
        }
        AtomicReference<OrderAndLocation> oal = new AtomicReference<>(getOAL());
        try {
            makeOrderValid(oal);
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): downloading certificate");
            String certs = client.downloadCertificate(getAccountURL(), hosts, oal.get().getOrder().getCertificate());

            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): promoting key+cert to production");
            asse.setKeyPair(hosts, client.getOALKey(hosts));
            asse.setCertChain(hosts, certs);

            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): retiring OAL");
            asse.archiveOAL(hosts);

        } catch (Exception e) {
            client.setOALError(hosts, new AcmeErrorLog(e.getClass().getName() + " " + e.getMessage(), e instanceof FatalAcmeException, new DateTime()));
            throw e;
        }
    }

    private void makeOrderValid(AtomicReference<OrderAndLocation> oal) throws Exception {
        oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()));
        if (LOG.isDebugEnabled())
            LOG.debug("acme ("+id()+"): order is " + oal.get().getOrder().getStatus());
        if (ORDER_STATUS_PENDING.equals(oal.get().getOrder().getStatus())) {
            fulfillChallenges(oal.get());
            oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()));
            waitFor(
                    "order to become non-'PENDING'",
                    () -> !ORDER_STATUS_PENDING.equals(oal.get().getOrder().getStatus()),
                    () -> {
                        oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()));
                    });
            if (!ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus()))
                throw new FatalAcmeException("order status " + om.writeValueAsString(oal));
        }
        if (ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus())) {
            if (client.getOALKey(hosts) == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("acme ("+id()+"): generating certificate key");
                AcmeKeyPair key = client.generateCertificateKey(hosts);
                client.setOALKey(hosts, key);
            }
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): finalizing order");
            client.finalizeOrder(getAccountURL(), oal.get().getOrder().getFinalize(), client.generateCSR(hosts, client.getOALKey(hosts).getPrivateKey()));
        }
        waitFor(
                "order to become 'VALID'",
                () -> !ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus()) && !ORDER_STATUS_PROCESSING.equals(oal.get().getOrder().getStatus()),
                () -> {
                    oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()));
                }
        );
        if (!ORDER_STATUS_VALID.equals(oal.get().getOrder().getStatus()))
            throw new FatalAcmeException("order status " + om.writeValueAsString(oal));
    }

    private String id() {
        return hosts[0] + (hosts.length > 1 ? ",..." : "");
    }

    private void fulfillChallenges(OrderAndLocation oal) throws Exception {
        for (String authorization : oal.getOrder().getAuthorizations()) {
            AtomicReference<Authorization> auth = new AtomicReference<>(client.getAuth(getAccountURL(), authorization));
            AtomicReference<Challenge> challenge = new AtomicReference<>(getChallenge(auth.get()));
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): authorization is " + auth.get().getStatus() + ", challenge is " + challenge.get().getStatus());

            if (CHALLENGE_STATUS_PENDING.equals(challenge.get().getStatus())) {
                if (LOG.isDebugEnabled())
                    LOG.debug("acme ("+id()+"): provisioning challenge");
                String challengeUrl = client.provision(auth.get());
                if (LOG.isDebugEnabled())
                    LOG.debug("acme ("+id()+"): triggering challenge check");
                client.readyForChallenge(getAccountURL(), challengeUrl);
            }
            waitFor(
                    "challenge and authorization to become non-'PENDING'",
                    () -> !CHALLENGE_STATUS_PENDING.equals(challenge.get().getStatus()) || !AUTHORIZATION_STATUS_PENDING.equals(auth.get().getStatus()),
                    () -> {
                        auth.set(client.getAuth(getAccountURL(), authorization));
                        challenge.set(getChallenge(auth.get()));
                    }
            );
            if (!CHALLENGE_STATUS_VALID.equals(challenge.get().getStatus()))
                throw new FatalAcmeException(challenge.get().getStatus() + " during " + om.writeValueAsString(auth));
            if (!AUTHORIZATION_STATUS_VALID.equals(auth.get().getStatus()))
                throw new FatalAcmeException(auth.get().getStatus() + " during " + om.writeValueAsString(auth));
        }
    }

    private void waitFor(String what, Supplier<Boolean> condition, Runnable job) throws Exception {
        if (LOG.isDebugEnabled())
            LOG.debug("acme ("+id()+"): waiting for " + what);
        long now = System.currentTimeMillis();
        long wait = 1000;
        while (!condition.get()) {
            Thread.sleep(wait);
            if (wait < 20 * 1000)
                wait = wait * 2;
            job.run();
            if (System.currentTimeMillis() - now > 5 * 60 * 1000) {
                throw new RuntimeException("Timeout (5min) while waiting for "+what+".");
            }
        }
    }

    private Challenge getChallenge(Authorization auth) throws JsonProcessingException, FatalAcmeException {
        Optional<Challenge> challenge = auth.getChallenges().stream().filter(c -> TYPE_HTTP_01.equals(c.getType())).findAny();
        if (!challenge.isPresent())
            throw new FatalAcmeException("Could not find challenge of type http01: " + om.writeValueAsString(auth));
        return challenge.get();
    }

    private void verifyAccountContact() {
        String contacts = client.getContacts().stream().collect(Collectors.joining(","));
        if (asse.getAccountContacts() == null) {
            asse.setAccountContacts(contacts);
        } else {
            if (!contacts.equals(asse.getAccountContacts()))
                throw new RuntimeException("It looks like you pointed an ACME client configured with '" + contacts + "' as contact to a storage where a key for '" + asse.getAccountContacts() + "' is present.");
        }
    }

    private OrderAndLocation getOAL() throws JsonProcessingException {
        String oal = asse.getOAL(hosts);
        if (oal == null)
            return null;
        return om.readValue(oal, OrderAndLocation.class);
    }

    private void setOAL(OrderAndLocation oal) throws JsonProcessingException {
        asse.setOAL(hosts, om.writeValueAsString(oal));
    }

    private String getAccountURL() {
        return asse.getAccountURL();
    }

    private void setAccountURL(String url) {
        asse.setAccountURL(url);
    }

    @FunctionalInterface
    public interface Supplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface Runnable {
        public abstract void run() throws Exception;
    }

    private boolean requiresWork() {
        String certificates = client.getCertificates(hosts);
        if (certificates == null)
            return true;
        try {
            List<Certificate> certs = new ArrayList<>(PEMSupport.getInstance().parseCertificates(certificates));
            if (certs.size() == 0)
                return true;

            long validUntil = getMinimumValidity(certs);

            return System.currentTimeMillis() > validUntil - RENEW_PERIOD;
        } catch (IOException e) {
            LOG.warn("Error parsing ACME certificate " + Arrays.toString(hosts), e);
            return true;
        }
    }

    private boolean isOALExpiredOrError() throws JsonProcessingException {
        OrderAndLocation oal = getOAL();
        if (oal != null && oal.getOrder().getExpires().isAfterNow())
            return true;
        AcmeErrorLog error = client.getOALError(hosts);
        if (error != null) {
            long wait = error.getTime().getMillis() + ERROR_WAIT_MILLISECONDS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    LOG.warn("Waiting " + (wait / 1000) + " seconds after ACME order error...");
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
        return false;
    }

    private boolean withMasterLease(Runnable runnable) {
        if (!client.getAsse().acquireLease(LEASE_DURATION_MILLISECONDS))
            return false;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (InterruptedException e) {
                    // do nothing
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        };
        t.start();
        while (true) {
            try {
                t.join(LEASE_RENEW_MILLISECONDS);
                if (!t.isAlive()) {
                    client.getAsse().releaseLease();
                    if (error.get() != null)
                        throw new RuntimeException(error.get());
                    return true;
                }
            } catch (InterruptedException e) {
                t.interrupt();
                Thread.currentThread().interrupt();
            }
            if (!client.getAsse().prolongLease(LEASE_DURATION_MILLISECONDS)) {
                t.interrupt();
                return true;
            }
        }
    }

}
