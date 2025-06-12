/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.predic8.membrane.core.transport.ssl.PEMSupport;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.transport.ssl.AcmeSSLContext.renewAt;
import static com.predic8.membrane.core.transport.ssl.SSLContext.getMinimumValidity;
import static com.predic8.membrane.core.transport.ssl.SSLContext.getValidFrom;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_PENDING;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.AUTHORIZATION_STATUS_VALID;
import static com.predic8.membrane.core.transport.ssl.acme.Challenge.*;
import static com.predic8.membrane.core.transport.ssl.acme.Order.*;

public class AcmeRenewal {
    private static final Logger LOG = LoggerFactory.getLogger(AcmeRenewal.class);
    private static final long ERROR_WAIT_MILLISECONDS = 15 * 60 * 1000;
    private static final long LEASE_DURATION_MILLISECONDS = 5 * 60 * 1000;
    private static final long LEASE_RENEW_MILLISECONDS = 4 * 60 * 1000;

    private final AcmeSynchronizedStorageEngine asse;
    private final String[] hosts;
    private final AcmeClient client;
    private final com.predic8.membrane.core.transport.ssl.AcmeSSLContext ctx; // Added field for AcmeSSLContext
    private final ObjectMapper om;

    public AcmeRenewal(AcmeClient client, String[] hosts, com.predic8.membrane.core.transport.ssl.AcmeSSLContext ctx) {
        this.client = client;
        this.asse = client.getAsse();
        this.hosts = hosts;
        this.ctx = ctx; // Store AcmeSSLContext
        this.om = new ObjectMapper().registerModule(new JodaModule());
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
                    Thread.sleep(ERROR_WAIT_MILLISECONDS);
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
            String certs = client.downloadCertificate(getAccountURL(), oal.get().getOrder().getCertificate());

            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): promoting key+cert to production");
            asse.setKeyPair(hosts, client.getOALKey(hosts));
            asse.setCertChain(hosts, certs);

            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): retiring OAL");
            asse.archiveOAL(hosts);

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            client.setOALError(hosts, new AcmeErrorLog(sw.toString(), e instanceof FatalAcmeException, new DateTime()));
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
                    () -> oal.set(client.getOrder(getAccountURL(), oal.get().getLocation())));
            if (!ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus()))
                throw new FatalAcmeException("order status " + om.writeValueAsString(oal));
        }
        if (ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus())) {
            if (client.getOALKey(hosts) == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("acme ("+id()+"): generating certificate key");
                AcmeKeyPair key = client.generateCertificateKey();
                client.setOALKey(hosts, key);
            }
            if (LOG.isDebugEnabled())
                LOG.debug("acme ("+id()+"): finalizing order");
            try {
                client.finalizeOrder(getAccountURL(), oal.get().getOrder().getFinalize(), client.generateCSR(hosts, client.getOALKey(hosts).getPrivateKey()));
            } catch (AcmeException e) {
                if (e.getMessage().contains("urn:ietf:params:acme:error:orderNotReady Order's status (\"valid\") is not acceptable for finalization")) {
                    // order is already valid. reretrieve it and continue
                    oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()));
                    return;
                }
                throw e;
            }
        }
        waitFor(
                "order to become 'VALID'",
                () -> !ORDER_STATUS_READY.equals(oal.get().getOrder().getStatus()) && !ORDER_STATUS_PROCESSING.equals(oal.get().getOrder().getStatus()),
                () -> oal.set(client.getOrder(getAccountURL(), oal.get().getLocation()))
        );
        if (!ORDER_STATUS_VALID.equals(oal.get().getOrder().getStatus()))
            throw new FatalAcmeException("order status " + om.writeValueAsString(oal));
    }

    private String id() {
        return hosts[0] + (hosts.length > 1 ? ",..." : "");
    }

    private void fulfillChallenges(OrderAndLocation oal) throws Exception {
        for (String authorizationUrl : oal.getOrder().getAuthorizations()) {
            AtomicReference<Authorization> auth = new AtomicReference<>(client.getAuth(getAccountURL(), authorizationUrl));

            // Determine initial challenge status (if possible, though client.provision makes the choice)
            // This part is tricky because we don't know the challenge type *until* provision is called.
            // So, initial status check for a *specific* challenge before provision is less meaningful.
            // We rely on provision to pick one.

            if (AUTHORIZATION_STATUS_VALID.equals(auth.get().getStatus())) {
                LOG.info("acme (" + id() + "): authorization {} already valid.", auth.get().getIdentifier().getValue());
                continue;
            }

            if (!AUTHORIZATION_STATUS_PENDING.equals(auth.get().getStatus())) {
                 throw new FatalAcmeException("Authorization status for " + auth.get().getIdentifier().getValue() + " is '" + auth.get().getStatus() + "', not 'pending'. Cannot proceed.");
            }

            LOG.info("acme ("+id()+"): provisioning challenge for " + auth.get().getIdentifier().getValue());
            AcmeClient.ProvisionResult provisionResult = null;
            try {
                provisionResult = client.provision(auth.get(), this.ctx);
                String challengeUrl = provisionResult.getChallengeUrl();

                // Re-fetch auth to get the latest challenge list and find the one matching the URL
                auth.set(client.getAuth(getAccountURL(), authorizationUrl));
                Challenge provisionedChallenge = findChallengeByUrl(auth.get(), challengeUrl);

                if (provisionedChallenge == null) {
                    throw new FatalAcmeException("Provisioned challenge URL " + challengeUrl + " not found in authorization " + auth.get().getIdentifier().getValue());
                }

                LOG.debug("acme ("+id()+"): authorization is " + auth.get().getStatus() + ", chosen challenge ("+ provisionedChallenge.getType() +") is " + provisionedChallenge.getStatus());

                if (CHALLENGE_STATUS_PENDING.equals(provisionedChallenge.getStatus())) {
                    LOG.debug("acme ("+id()+"): triggering challenge check for " + auth.get().getIdentifier().getValue() + " via " + challengeUrl);
                    client.readyForChallenge(getAccountURL(), challengeUrl);
                }

                // Use AtomicReference for the specific challenge being processed
                AtomicReference<Challenge> currentChallengeRef = new AtomicReference<>(provisionedChallenge);

                waitFor(
                        "challenge for " + auth.get().getIdentifier().getValue() + " to become non-'PENDING'",
                        () -> {
                            Challenge c = currentChallengeRef.get();
                            return c != null && !CHALLENGE_STATUS_PENDING.equals(c.getStatus());
                        },
                        () -> {
                            auth.set(client.getAuth(getAccountURL(), authorizationUrl));
                            currentChallengeRef.set(findChallengeByUrl(auth.get(), challengeUrl));
                            if (currentChallengeRef.get() == null) {
                                throw new FatalAcmeException("Challenge with URL " + challengeUrl + " disappeared from authorization " + auth.get().getIdentifier().getValue());
                            }
                        }
                );

                if (!CHALLENGE_STATUS_VALID.equals(currentChallengeRef.get().getStatus())) {
                    throw new FatalAcmeException("Challenge status for " + auth.get().getIdentifier().getValue() + " (" + currentChallengeRef.get().getType() + ") is '" + currentChallengeRef.get().getStatus() + "', not 'valid'. Error: " + om.writeValueAsString(currentChallengeRef.get().getOther().get("error")));
                }

                // Also wait for overall Authorization to be valid
                waitFor(
                        "authorization for " + auth.get().getIdentifier().getValue() + " to become 'VALID'",
                        () -> AUTHORIZATION_STATUS_VALID.equals(auth.get().getStatus()),
                        () -> auth.set(client.getAuth(getAccountURL(), authorizationUrl))
                );

                if (!AUTHORIZATION_STATUS_VALID.equals(auth.get().getStatus())) {
                    throw new FatalAcmeException("Authorization status for " + auth.get().getIdentifier().getValue() + " is '" + auth.get().getStatus() + "', not 'valid'.");
                }

                LOG.info("acme ("+id()+"): successfully validated challenge for " + auth.get().getIdentifier().getValue());

            } finally {
                if (provisionResult != null) {
                    provisionResult.performCleanup();
                    LOG.info("acme ("+id()+"): performed cleanup for challenge of " + auth.get().getIdentifier().getValue());
                }
            }
        }
    }

    private Challenge findChallengeByUrl(Authorization auth, String url) {
        return auth.getChallenges().stream().filter(c -> url.equals(c.getUrl())).findFirst().orElse(null);
    }

    private void waitFor(String what, Supplier<Boolean> condition, Runnable job) throws Exception {
        if (LOG.isDebugEnabled())
            LOG.debug("acme ("+id()+"): waiting for " + what);
        long now = System.currentTimeMillis();
        long wait = 1000;
        while (!condition.get()) {
            Thread.sleep(wait);
            if (wait < 20 * 1000) // Max wait interval: 20 seconds
                wait = Math.min(wait * 2, 20000); // Double wait time, capped at 20s
            job.run();
            if (System.currentTimeMillis() - now > 5 * 60 * 1000) { // Total timeout: 5 minutes
                throw new RuntimeException("Timeout (5min) while waiting for "+what+".");
            }
        }
    }

    // Removed getChallenge(Authorization auth) as client.provision now handles selection.

    private void verifyAccountContact() {
        String contacts = String.join(",", client.getContacts());
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

            long validFrom = getValidFrom(certs);
            long validUntil = getMinimumValidity(certs);

            return System.currentTimeMillis() > renewAt(validFrom, validUntil);
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

    private void withMasterLease(Runnable runnable) {
        if (!client.getAsse().acquireLease(LEASE_DURATION_MILLISECONDS))
            return;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                runnable.run();
            } catch (InterruptedException e) {
                // do nothing
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        while (true) {
            try {
                t.join(LEASE_RENEW_MILLISECONDS);
                if (!t.isAlive()) {
                    client.getAsse().releaseLease();
                    if (error.get() != null)
                        throw new RuntimeException(error.get());
                    return;
                }
            } catch (InterruptedException e) {
                t.interrupt();
                Thread.currentThread().interrupt();
            }
            if (!client.getAsse().prolongLease(LEASE_DURATION_MILLISECONDS)) {
                t.interrupt();
                return;
            }
        }
    }

}
