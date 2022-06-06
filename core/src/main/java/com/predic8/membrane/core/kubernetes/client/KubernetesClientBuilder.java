package com.predic8.membrane.core.kubernetes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.Trust;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KubernetesClientBuilder {
    private static final String TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String CA_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String NAMESPACE_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    private boolean logHttp = false;

    String baseURL;
    String ca;
    String cert;
    String key;
    String token;
    String namespace;

    KubernetesClientBuilder(String baseURL, String ca, String cert, String key, String token, String namespace) {
        this.baseURL = baseURL;
        this.ca = ca;
        this.cert = cert;
        this.key = key;
        this.token = token;
        this.namespace = namespace;
    }


    public static KubernetesClientBuilder auto() throws ParsingException {
        if (new File(TOKEN_FILE).exists())
            return inClusterConfig();
        return kubeConfig();
    }

    private static KubernetesClientBuilder inClusterConfig() throws ParsingException {
        try {
            String baseURL = "https://kubernetes.default.svc";

            String ca = Files.asCharSource(new File(CA_FILE), Charsets.UTF_8).read();
            String token = Files.asCharSource(new File(TOKEN_FILE), Charsets.UTF_8).read();
            String namespace = Files.asCharSource(new File(NAMESPACE_FILE), Charsets.UTF_8).read();

            return new KubernetesClientBuilder(baseURL, ca, null, null, token, namespace);
        } catch (IOException e) {
            throw new ParsingException("while building in-cluster KubernetesClient config", e);
        }
    }

    private static KubernetesClientBuilder kubeConfig() throws ParsingException {
        try {
            File baseDir = new File(new File(System.getProperty("user.home")), ".kube");

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map kubeConfig = mapper.readValue(new File(baseDir, "config"), Map.class);

            String currentContext = (String) kubeConfig.get("current-context");
            Map context = ((List<Map>) kubeConfig.get("contexts")).stream().filter(m -> m.get("name").equals(currentContext)).findFirst().orElse(null);
            String currentCluster = (String) ((Map)context.get("context")).get("cluster");
            String currentUser = (String) ((Map)context.get("context")).get("user");
            String namespace = (String) ((Map)context.get("context")).get("namespace");
            Map cluster = ((List<Map>) kubeConfig.get("clusters")).stream().filter(m -> m.get("name").equals(currentCluster)).findFirst().orElse(null);

            String baseURL = (String) ((Map)cluster.get("cluster")).get("server");

            Map user = ((List<Map>) kubeConfig.get("users")).stream().filter(m -> m.get("name").equals(currentUser)).findFirst().orElse(null);

            String ca = getReferencedFile(baseDir, (String) ((Map)cluster.get("cluster")).get("certificate-authority"));

            String cert = null, key = null, token = null;
            if (user != null) {
                token = (String) ((Map)user.get("user")).get("token");
                if (token == null) {
                    Map authP = (Map) ((Map) user.get("user")).get("auth-provider");
                    if (authP != null) {
                        Map config = (Map)authP.get("config");
                        if (config != null) {
                            token = (String) config.get("id-token");
                        }
                    }
                }

                cert = getReferencedFile(baseDir, (String) ((Map)user.get("user")).get("client-certificate"));
                key = getReferencedFile(baseDir, (String) ((Map)user.get("user")).get("client-key"));
            }

            return new KubernetesClientBuilder(baseURL, ca, cert, key, token, namespace);
        } catch (IOException e) {
            throw new ParsingException("while parsing ~/.kube/config", e);
        }
    }

    public KubernetesClient build() {
        HttpClient hc = new HttpClient();
        Consumer<Exchange> client = hc::call;

        if (baseURL.startsWith("https")) {
            SSLParser sslParser = new SSLParser();
            if (key != null && cert != null) {
                Key key1 = new Key();
                Key.Private private_ = new Key.Private();
                private_.setContent(key);
                key1.setPrivate(private_);
                key1.setCertificates(certList(cert));
                sslParser.setKey(key1);
            }
            if (ca != null) {
                Trust trust = new Trust();
                trust.setCertificateList(certList(ca));
                sslParser.setTrust(trust);
            }
            StaticSSLContext sslContext = new StaticSSLContext(sslParser, null, null);
            Consumer<Exchange> lastClient = client;
            client = exchange -> {
                exchange.setProperty(Exchange.SSL_CONTEXT, sslContext);
                lastClient.call(exchange);
            };
        }
        if (token != null) {
            Consumer<Exchange> lastClient = client;
            String theToken = token;
            client = exchange -> {
                exchange.getRequest().getHeader().add("Authorization", "Bearer " + theToken);
                lastClient.call(exchange);
            };
        }
        if (logHttp) {
            Consumer<Exchange> lastClient = client;
            LogInterceptor i = new LogInterceptor();
            i.setLevel(LogInterceptor.Level.WARN);
            i.setHeaderOnly(false);
            client = exchange -> {
                i.handleRequest(exchange);
                lastClient.call(exchange);
                i.handleResponse(exchange);
            };
        }
        return new KubernetesClient(client, baseURL, namespace);
    }

    private static List<Certificate> certList(String certs) {
        ArrayList<Certificate> certificates = new ArrayList<>();
        for (String cert : certs.split("(?<=-----)\r?\n(?=-----)")) {
            Certificate cert1 = new Certificate();
            cert1.setContent(cert);
            certificates.add(cert1);
        }
        return certificates;
    }

    private static String getReferencedFile(File baseDir, String filePath) throws IOException {
        if (filePath == null)
            return null;
        File file = new File(filePath);
        File absolutePath = file.isAbsolute() ? file : new File(baseDir, filePath);
        return Files.asCharSource(absolutePath, Charsets.UTF_8).read();
    }

    public KubernetesClientBuilder log(boolean log) {
        this.logHttp = log;
        return this;
    }

    public static class ParsingException extends Exception {
        public ParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
