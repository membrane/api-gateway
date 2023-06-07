package com.predic8.membrane.core.azure;

import com.predic8.membrane.core.azure.api.AzureApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

class AzureApiClientTest {

    AzureDnsApiSimulator simulator;
    AzureApiClient apiClient;

    @BeforeEach
    void setup() throws IOException {
        var tableStorage = new AzureTableStorage();
        tableStorage.setStorageAccountName("hello");
        tableStorage.setStorageAccountKey("thisisasecretandshouldhaveenoughbits");

        int port = 3050;

        tableStorage.setCustomHost("http://localhost:" + port);

        simulator = new AzureDnsApiSimulator(port);
        simulator.start();

        apiClient = new AzureApiClient(null, tableStorage, null);
    }

    @AfterEach
    void tearDown() {
        simulator.stop();
    }
}
