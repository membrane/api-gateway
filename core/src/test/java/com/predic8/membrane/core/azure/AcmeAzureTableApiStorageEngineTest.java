package com.predic8.membrane.core.azure;

import com.predic8.membrane.core.transport.ssl.acme.AcmeAzureTableApiStorageEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AcmeAzureTableApiStorageEngineTest {

    private AcmeAzureTableApiStorageEngine engine;

    @BeforeAll
    void setup() {
        var tableStorage = new AzureTableStorage();
        tableStorage.setStorageAccountName("");
        tableStorage.setStorageAccountKey("");


        engine = new AcmeAzureTableApiStorageEngine(
            tableStorage,
                null,
                null
        );
    }



    @Test
    void createTable() {

    }

    @Test
    void ignoreExistingTable() {

    }


}

