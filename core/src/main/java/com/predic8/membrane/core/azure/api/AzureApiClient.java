package com.predic8.membrane.core.azure.api;

import com.predic8.membrane.core.azure.AzureConfig;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

public class AzureApiClient implements AutoCloseable {

    private final HttpClient httpClient;
    private final AzureConfig azureConfig;
    private final AuthenticationApi authApi;
    private final DnsRecordApi dnsApi;
    private final TableStorageApi tableStorageApi;


    public AzureApiClient(AzureConfig azureConfig) {
        this.azureConfig = azureConfig;
        this.httpClient = new HttpClient();

        authApi = new AuthenticationApi(httpClient, azureConfig);
        dnsApi = new DnsRecordApi(this);
        tableStorageApi = new TableStorageApi(this);
    }

    public DnsRecordApi dnsRecords() {
        return dnsApi;
    }

    public TableStorageApi tableStorage() {
        return tableStorageApi;
    }

    protected AuthenticationApi auth() {
        return authApi;
    }

    protected AzureConfig config() {
        return azureConfig;
    }

    protected HttpClient httpClient() {
        return httpClient;
    }

    protected Request.Builder authenticatedRequestBuilder() throws Exception {
        return new Request.Builder()
                .header("Authorization", "Bearer " + auth().accessToken());
    }

    protected Request.Builder storageAccountRequestBuilder(String announceUrl) throws Exception {
        var date = DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"))
                .format(Instant.now());

        var stringToSign = buildStringToSign(date, announceUrl);
        var signature = sign(config().getStorageAccountKey(), stringToSign);

        return new Request.Builder()
                .contentType("application/json")
                .header("Date", date)
                .header("x-ms-version", "2020-12-06")
                .header("Accept", "application/json")
                .header("DataServiceVersion", "3.0;NetFx")
                .header("MaxDataServiceVersion", "3.0;NetFx")
                .header("Authorization",
                        String.format("SharedKeyLite %s:%s", config().getStorageAccountName(), signature));
    }

    private String buildStringToSign(String date, String url) {
        var base = date + "\n/" + config().getStorageAccountName();

        return date == null
                ? base + "/Tables"
                : base + url.substring(url.lastIndexOf("/" + config().getTableName()));
    }

    private String sign(String base64Key, String stringToSign) throws Exception {
        byte[] key = java.util.Base64.getDecoder().decode(base64Key);
        Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
        hmacSHA256.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] utf8Bytes = stringToSign.getBytes(StandardCharsets.UTF_8);

        return Base64.getEncoder().encodeToString(hmacSHA256.doFinal(utf8Bytes));
    }

    @Override
    public void close() throws Exception {
        this.httpClient.close();
    }
}
