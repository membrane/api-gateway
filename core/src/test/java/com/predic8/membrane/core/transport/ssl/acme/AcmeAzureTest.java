package com.predic8.membrane.core.transport.ssl.acme;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

/**
 * <a href="https://learn.microsoft.com/en-us/java/api/overview/azure/data-tables-readme?view=azure-java-stable#create-list-and-delete-table-entities">Azure Tables</a>
 * Key concepts
 * TableServiceClient - A TableServiceClient is a client object that enables you to interact with the Table Service in order to create, list, and delete tables.
 * TableClient - A TableClient is a client object that enables you to interact with a specific table in order to create, upsert, update, get, list, and delete entities within it.
 * Table - A table is a collection of entities. Tables don't enforce a schema on entities, which means a single table can contain entities that have different sets of properties.
 * Entity - An entity is a set of properties, similar to a database row. An entity in Azure Storage can be up to 1MB in size. An entity in Azure Cosmos DB can be up to 2MB in size. An entity has a partition key and a row key which together uniquely identify the entity within the table.
 * Properties - A property is a name-value pair. Each entity can include up to 252 properties to store data. Each entity also has three system properties that specify a partition key, a row key, and a timestamp.
 * Partition Key - An entity's partition key identifies the partition within the table to which the entity belongs. Entities with the same partition key can be queried more quickly, and inserted/updated in atomic operations.
 * Row Key - An entity's row key is its unique identifier within a partition.
 */
public class AcmeAzureTest {

    final String TABLE_NAME = "membrane";
    final String PARTITION_NAME = "acme";
    final String CONNECTION_STRING = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";
    TableClient tableClient;

    @BeforeEach
    void init() {
        tableClient = new TableClientBuilder()
                .connectionString(CONNECTION_STRING)
                .tableName(TABLE_NAME)
                .buildClient();

        var tableItem = tableClient.createTable();
        System.out.printf("Created table with name '%s'%n", tableItem.getName());
    }

    @AfterEach
    void cleanup() {
        System.out.println("Cleaning table");
        tableClient.deleteTable();
    }

    @Test
    void createEntry() {
        var rowKey = "acccount";

        var entity = new TableEntity(PARTITION_NAME, rowKey)
                .addProperty("data", "this would be the account value");

        tableClient.createEntity(entity);

        var end = tableClient.getEntity(PARTITION_NAME, rowKey);

        System.out.println(end.getProperty("data"));

    }

    @Test
    void getNotExistingEntity() {
        var entity = tableClient.getEntity("acme", "foo");
    }

    void listEntry(String... properties) {
        var options = new ListEntitiesOptions()
                .setSelect(Arrays.asList(properties));

        for (var listedEntity : tableClient.listEntities(options, null, null)) {
            for (var property : properties) {
                System.out.printf("%s: %s", property, listedEntity.getProperty(property));
            }
            System.out.println();
        }
    }

    void listEntry() {
        for (var listedEntity : tableClient.listEntities()) {
            System.out.println(listedEntity.getProperties());
        }
    }

    void deleteEntity(String partitionKey, String rowKey) {
        tableClient.deleteEntity(partitionKey, rowKey);
    }
}
