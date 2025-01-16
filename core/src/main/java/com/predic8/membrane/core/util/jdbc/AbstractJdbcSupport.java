package com.predic8.membrane.core.util.jdbc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;

import javax.sql.DataSource;
import java.util.Map;

public abstract class AbstractJdbcSupport {

    private DataSource datasource;
    private Router router;
    private static final String DATASOURCE_SAMPLE = """
             Sample:
            
                <spring:bean id="yourDataSource" class="org.apache.commons.dbcp.BasicDataSource">
                    <spring:property name="driverClassName" value="yourDriver"> />
                    <spring:property name="url" value="jdbc:mysql://localhost:3306/yourDatabase" />
                    <spring:property name="username" value="yourUsername" />
                    <spring:property name="password" value="yourPassword" />
                </spring:bean>
            
                 <router>
                    <api port="2000" />
                        <apiKey>
                            <databaseApiKeyStore datasource="yourDataSource">
                                <keyTable>key</keyTable>
                                <scopeTable>scope</scopeTable>
                            </databaseApiKeyStore>
                        </apiKey>
                    </api>
                </router>
            """;

    public void init(Router router) {
        this.router = router;
        getDatasourceIfNull();
    }

    private void getDatasourceIfNull() {
        if (datasource != null)
            return;

        Map<String, DataSource> beans = router.getBeanFactory().getBeansOfType(DataSource.class);

        DataSource[] datasources = beans.values().toArray(new DataSource[0]);
        if (datasources.length == 0) {
            datasource = datasources[0];
            return;
        }
        if (datasources.length > 1) {
            throw new ConfigurationException("""
                        More than one DataSource found in configuration. Specify the dataSource name explicitly.
                        %s
                    """.formatted(DATASOURCE_SAMPLE));
        }
        throw new RuntimeException("""
                No datasource found - specifiy a DataSource bean in your configuration
                %s
                """.formatted(DATASOURCE_SAMPLE));
    }

    @MCAttribute
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public DataSource getDatasource() {
        return datasource;
    }
}
