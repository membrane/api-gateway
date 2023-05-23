package com.predic8.membrane.core.transport.ssl.acme;

import com.predic8.membrane.core.config.security.acme.AzureTableStorage;
import org.junit.jupiter.api.BeforeEach;

public class AcmeAzureTableApiStorageEngineTest {

    // azurite emulator connection string is well-known
    private static final String CONNECTION_STRING = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";

    public AcmeAzureTableApiStorageEngine engine;

    @BeforeEach
    void init() {
        var client = new AzureTableStorage();
        client.setConnectionString(CONNECTION_STRING);
        engine = new AcmeAzureTableApiStorageEngine(client);
    }
}
