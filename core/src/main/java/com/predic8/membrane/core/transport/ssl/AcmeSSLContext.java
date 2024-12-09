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
package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.ssl.acme.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.Key;
import java.security.KeyStore;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.*;

public class AcmeSSLContext extends SSLContext {
    private static final Logger log = LoggerFactory.getLogger(AcmeSSLContext.class);
    public static final int TLS_CERTIFICATE_UNKNOWN = 46;

    private final SSLParser parser;
    private final AcmeClient client;
    private final String[] hosts;
    private final boolean selfCreatedTimerManager;
    private final TimerManager timerManager;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private volatile AcmeKeyCert keyCert;


    public AcmeSSLContext(SSLParser parser,
                          String[] hosts,
                          @Nullable HttpClientFactory httpClientFactory,
                          @Nullable TimerManager timerManager) {
        this.parser = parser;
        this.hosts = computeHostList(hosts, parser.getAcme().getHosts());
        client = new AcmeClient(parser.getAcme(), httpClientFactory);
        selfCreatedTimerManager = timerManager == null;
        this.timerManager = timerManager != null ? timerManager : new TimerManager();
    }

    public void init(@Nullable KubernetesClientFactory kubernetesClientFactory, @Nullable HttpClientFactory httpClientFactory) {
        client.init(kubernetesClientFactory, httpClientFactory);
        initAndSchedule();
    }

    private String[] computeHostList(String[] hostsWantedByRule, String hostsRequestedForCertificate) {
        if (hostsRequestedForCertificate == null)
            return hostsWantedByRule;
        String[] cs = hostsRequestedForCertificate.split(" +");
        for (String h : hostsWantedByRule) {
            boolean fulfilled = false;
            for (String c : cs)
                if (hostMatches(h, c))
                    fulfilled = true;
            if (!fulfilled)
                throw new RuntimeException("Hostname " + h + " seems not to be fulfillable by a certificate issued for " + hostsRequestedForCertificate);
        }
        return cs;
    }

    private boolean hostMatches(String host, String certificateHost) {
        if (host.equals(certificateHost))
            return true;
        if (certificateHost.startsWith("*.") && host.endsWith(certificateHost.substring(2)) && host.length() >= certificateHost.length() && host.codePointAt(host.length() - certificateHost.length() + 1) == TLS_CERTIFICATE_UNKNOWN && isHostname(host.substring(0, host.length() - certificateHost.length() + 1)))
            return true;
        return false;
    }

    /**
     * Note that we not only allow digits, letters and hyphens, but also the asterisk, as this will translate to a hostname pattern.
     */
    private boolean isHostname(String expr) {
        for (int i = 0; i < expr.length(); i++)
            if (! (Character.isDigit(expr.codePointAt(i)) || Character.isLetter(expr.codePointAt(i)) || (expr.codePointAt(i) == 45) || (expr.codePointAt(i) == 42)))
                return false;
        return true;
    }

    @Override
    String getLocation() {
        return "ACME certificate from " + constructHostsString();
    }

    @Override
    List<String> getDnsNames() {
        return Arrays.asList(hosts);
    }

    @Override
    SSLSocketFactory getSocketFactory() {
        AcmeKeyCert context = this.keyCert;
        if (context == null)
            throw new RuntimeException("ACME has not yet acquired a certificate.");
        return context.getSslContext().getSocketFactory();
    }

    public AcmeClient getClient() {
        return client;
    }

    public String[] getHosts() {
        return hosts;
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
        return new ServerSocket(port, backlog, bindAddress);
    }

    @Override
    public Socket createSocket() throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, int connectTimeout, @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Socket createSocket(String host, int port, int connectTimeout, @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress addr, int localPort, int connectTimeout, @Nullable String sniServerName, @Nullable String[] applicationProtocols) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Socket wrapAcceptedSocket(Socket socket) throws IOException {
        byte[] buffer = new byte[0x0];
        int position = 0;

        return wrap(socket, buffer, position);
    }

    @Override
    public Socket wrap(Socket socket, byte[] buffer, int position) throws IOException {
        check(socket);
        return super.wrap(socket, buffer, position);
    }

    private void check(Socket socket) throws IOException {
        if (getSocketFactory() == null) {
            byte[] certificate_unknown = { 21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
                    2 /* fatal */, TLS_CERTIFICATE_UNKNOWN /* certificate_unknown */ };

            try (socket) {
                socket.getOutputStream().write(certificate_unknown);
            }

            throw new RuntimeException("no ACME certificate available for " + constructHostsString());
        }
    }

    private String constructHostsString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hosts.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(hosts[i]);
        }
        return sb.toString();
    }

    private void initAndSchedule() {
        // called from main and timer thread
        try {
            tryLoad();
        } catch (Exception e) {
            log.info("ACME: do not yet have a certificate for " + constructHostsString(), e);
        }
        schedule();
    }

    private void tryLoad() {
        javax.net.ssl.SSLContext sslc;

        String keyS = client.getKey(hosts);
        String certsS = client.getCertificates(hosts);
        if (keyS == null) {
            log.debug("ACME: do not yet have a key for " + constructHostsString());
            return;
        }
        if (certsS == null) {
            log.debug("ACME: do not yet have a certificate for " + constructHostsString());
            return;
        }
        AcmeKeyCert existing = this.keyCert;
        if (existing != null && keyS.equals(existing.getKey()) && certsS.equals(existing.getCerts()))
            // reloading would not change anything
            return;
        long validFrom, validUntil;

        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, "".toCharArray());

            List<Certificate> certs = getCertificates(certsS);

            checkChainValidity(certs);
            validFrom = getValidFrom(certs);
            validUntil = getMinimumValidity(certs);
            Key k = getKey(keyS);
            checkKeyMatchesCert(k, certs);

            ks.setKeyEntry("inlinePemKeyAndCertificate", k, "".toCharArray(),  certs.toArray(new Certificate[0]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "".toCharArray());

            sslc = javax.net.ssl.SSLContext.getInstance("TLS");
            sslc.init(kmf.getKeyManagers(), null,null);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(parser, sslc);

        this.keyCert = new AcmeKeyCert(keyS, certsS, validFrom, validUntil, sslc);
        log.info("ACME: installed key and certificate for " + constructHostsString());
    }

    private static Key getKey(String keyS) throws IOException {
        Object key = PEMSupport.getInstance().parseKey(keyS);
        return key instanceof Key ? (Key) key : ((KeyPair)key).getPrivate();
    }

    private static @NotNull List<Certificate> getCertificates(String certsS) throws IOException {
        List<Certificate> certs = new ArrayList<>(PEMSupport.getInstance().parseCertificates(certsS));
        if (certs.isEmpty())
            throw new RuntimeException("At least one certificate is required.");
        return certs;
    }

    public void schedule() {
        long nextRun = parser.getAcme().getRetry();
        AcmeKeyCert keyCert = this.keyCert;
        if (keyCert != null)
            nextRun = Math.max(
                    renewAt(keyCert.getValidFrom(), keyCert.getValidUntil()) - System.currentTimeMillis(),
                    parser.getAcme().getRetry());

        if (shutdown.get())
            return;

        timerManager.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!"never".equals(parser.getAcme().getRenewal()))
                    new AcmeRenewal(client, hosts).doWork();
                initAndSchedule();
            }
        }, nextRun, "ACME timer " + constructHostsString());
    }

    public static long renewAt(long validFrom, long validUntil) {
        return validUntil - (validUntil - validFrom) / 3;
    }

    @Override
    public void stop() {
        shutdown.set(true);
        if (selfCreatedTimerManager)
            timerManager.shutdown();
    }

    public boolean isReady() {
        return keyCert != null;
    }

    @Override
    public boolean hasKeyAndCertificate() {
        return keyCert != null;
    }

    @Override
    public long getValidFrom() {
        return keyCert.getValidFrom();
    }

    @Override
    public long getValidUntil() {
        return keyCert.getValidUntil();
    }

    @Override
    public String getPrometheusContextTypeName() {
        return "acme";
    }
}
