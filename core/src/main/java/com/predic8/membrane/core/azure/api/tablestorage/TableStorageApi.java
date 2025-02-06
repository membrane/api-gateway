/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.azure.api.tablestorage;

import com.predic8.membrane.core.azure.AzureTableStorage;
import com.predic8.membrane.core.azure.api.AzureApiClient;
import com.predic8.membrane.core.azure.api.HttpClientConfigurable;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

public class TableStorageApi implements HttpClientConfigurable<AzureTableStorage> {

    private final AzureApiClient apiClient;
    private final AzureTableStorage config;

    public TableStorageApi(AzureApiClient apiClient, AzureTableStorage config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    public TableStorageCommandExecutor table() {
        return new TableStorageCommandExecutor(this);
    }

    public TableEntityCommandExecutor entity(String rowKey) {
        return new TableEntityCommandExecutor(this, rowKey);
    }

    @Override
    public HttpClient http() {
        return apiClient.httpClient();
    }

    @Override
    public AzureTableStorage config() {
        return config;
    }

    protected Request.Builder requestBuilder(@Nullable String announceUrl) {
        var date = now();

        var stringToSign = buildStringToSign(date, announceUrl);
        var storageAccountKey = config.getStorageAccountKey();

        var signature = sign(storageAccountKey, stringToSign);

        var storageAccountName = config.getStorageAccountName();

        return new Request.Builder()
                .contentType("application/json")
                .header("Date", date)
                .header("x-ms-version", "2020-12-06")
                .header("Accept", "application/json")
                .header("DataServiceVersion", "3.0;NetFx")
                .header("MaxDataServiceVersion", "3.0;NetFx")
                .header("Authorization",
                        String.format("SharedKeyLite %s:%s", storageAccountName, signature));
    }

    private String now() {
        return DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"))
                .format(Instant.now());
    }

    private String buildStringToSign(String date, @Nullable String url) {
        var storageAccount = config.getStorageAccountName();
        var base = String.format("%s\n/%s", date, storageAccount);

        if (url == null) {
            return base + "/Tables";
        }

        return base + url.substring(url.lastIndexOf("/" + config.getTableName()));
    }

    private String sign(String base64Key, String stringToSign) {
        try {
            var algo = "HmacSHA256";
            byte[] key = java.util.Base64.getDecoder().decode(base64Key);
            Mac hmacSHA256 = Mac.getInstance(algo);
            hmacSHA256.init(new SecretKeySpec(key, algo));
            byte[] utf8Bytes = stringToSign.getBytes(StandardCharsets.UTF_8);

            return Base64.getEncoder().encodeToString(hmacSHA256.doFinal(utf8Bytes));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
