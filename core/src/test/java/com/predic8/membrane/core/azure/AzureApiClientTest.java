package com.predic8.membrane.core.azure;

import com.predic8.membrane.core.azure.api.AzureApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void createNonExistingTable() {
        assertDoesNotThrow(() -> apiClient.tableStorage().table().create());
    }

    @Test
    void ignoreExistingTable() {
        assertDoesNotThrow(() -> {
            apiClient.tableStorage().table().create();
            apiClient.tableStorage().table().create();
        });
    }

    @Test
    void insertWithoutBaseTable() {
        assertThrows(RuntimeException.class, () -> apiClient
                .tableStorage()
                .entity("account")
                .insertOrReplace("initial"));
    }

    @Test
    void createTableEntry() throws Exception {
        assertDoesNotThrow(() -> {
            apiClient.tableStorage().table().create();

            apiClient
                    .tableStorage()
                    .entity("account")
                    .insertOrReplace("initial");
        });

        var data = apiClient.tableStorage().entity("account").get()
                .get("data").asText();

        assertEquals("initial", data);
    }

    @Test
    void getNonExistingEntity() {
        assertDoesNotThrow(() -> apiClient.tableStorage().table().create());
        assertThrows(NoSuchElementException.class, () -> apiClient.tableStorage().entity("foo").get());
    }

    @Test
    void updateTableEntry() throws Exception {
        assertDoesNotThrow(() -> {
            apiClient.tableStorage().table().create();

            apiClient
                    .tableStorage()
                    .entity("account")
                    .insertOrReplace("initial");

            apiClient
                    .tableStorage()
                    .entity("account")
                    .insertOrReplace("updated");
        });

        var data = apiClient.tableStorage().entity("account").get()
                .get("data").asText();

        assertEquals("updated", data);
    }

    @Test
    void deleteExistingEntity() {
        assertDoesNotThrow(() -> {
            apiClient.tableStorage().table().create();
            apiClient.tableStorage().entity("account")
                    .insertOrReplace("initial");
        });
        assertDoesNotThrow(() -> apiClient.tableStorage().entity("account").get());
        assertDoesNotThrow(() -> apiClient.tableStorage().entity("account").delete());
    }

    @Test
    void deleteNonExisting() {
        assertDoesNotThrow(() -> apiClient.tableStorage().table().create());
        assertDoesNotThrow(() -> apiClient.tableStorage().entity("account").delete());
    }
}
