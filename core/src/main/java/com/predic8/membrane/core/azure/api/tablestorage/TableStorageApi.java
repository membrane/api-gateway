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

import com.predic8.membrane.core.azure.*;
import com.predic8.membrane.core.azure.api.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;

import javax.annotation.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;

public class TableStorageApi implements HttpClientConfigurable<AzureTableStorage> {

    public static final String ALGO = "HmacSHA256";
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
        return new Request.Builder()
                .contentType(APPLICATION_JSON)
                .header("Date", date)
                .header("x-ms-version", "2020-12-06")
                .header("Accept", APPLICATION_JSON)
                .header("DataServiceVersion", "3.0;NetFx")
                .header("MaxDataServiceVersion", "3.0;NetFx")
                .header("Authorization",
                        String.format("SharedKeyLite %s:%s", config.getStorageAccountName(), getSign(announceUrl, date)));
    }

    private String getSign(@org.jetbrains.annotations.Nullable String announceUrl, String date) {
        return sign(config.getStorageAccountKey(), buildStringToSign(date, announceUrl));
    }

    private String now() {
        return DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"))
                .format(Instant.now());
    }

    private String buildStringToSign(String date, @Nullable String url) {
        var base = String.format("%s\n/%s", date, config.getStorageAccountName());

        if (url == null) {
            return base + "/Tables";
        }

        return base + url.substring(url.lastIndexOf("/" + config.getTableName()));
    }

    private String sign(String base64Key, String stringToSign) {
        try {
            Mac hmacSHA256 = Mac.getInstance(ALGO);
            hmacSHA256.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), ALGO));
            return Base64.getEncoder().encodeToString(hmacSHA256.doFinal(stringToSign.getBytes(UTF_8)));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
