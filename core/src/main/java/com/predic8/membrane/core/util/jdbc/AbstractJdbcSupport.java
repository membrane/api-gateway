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
            
            <spring:beans xmlns="http://membrane-soa.org/proxies/1/"
                                   xmlns:spring="http://www.springframework.org/schema/beans"
                                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                   xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
                                            http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
             <spring:bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
                 <spring:property name="driverClassName" value="org.postgresql.Driver" />
                 <spring:property name="url" value="jdbc:postgresql://localhost:5432/postgres" />
                 <spring:property name="username" value="user" />
                 <spring:property name="password" value="password" />
             </spring:bean>

             <router>
                 <api port="2000">
                     <apiKey>
                         <databaseApiKeyStore datasource="dataSource">
                             <keyTable>key</keyTable>
                             <scopeTable>scope</scopeTable>
                         </databaseApiKeyStore>
                         <headerExtractor />
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
