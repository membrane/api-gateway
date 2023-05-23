package com.predic8.membrane.core.transport.ssl.acme;

import com.predic8.membrane.core.config.security.acme.AzureTableStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.util.Assert.isNull;

public class AcmeAzureTableApiStorageEngineTest {

    public AcmeAzureTableApiStorageEngine engine;

//    @BeforeEach
//    void init() {
//        var client = new AzureTableStorage();
//        client.setConnectionString("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;");
//        engine = new AcmeAzureTableApiStorageEngine(client);
//    }
//
//    @Test
//    void test() {
//        var accountKey = engine.getAccountKey();
//        isNull(accountKey, "Account should be null and not throw an exception");
//    }
}
