package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;

import javax.sql.DataSource;
import java.util.Map;

public abstract class AbstractJdbcUserDataProvider implements UserDataProvider {

    private DataSource datasource;
    private String tableName;
    private Router router;
    public abstract Map<String, String> verify(Map<String, String> postData);

    @Override
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
        } else if (datasources.length > 1) {
            throw new ConfigurationException("""
                More than one DataSource found in your Membrane configuration. Please specify the DataSource name explicitly.
                Sample configuration:
                <spring:bean id="yourDataSource" class="org.apache.commons.dbcp.BasicDataSource">
                    <spring:property name="driverClassName" value="yourDriver"> />
                    <spring:property name="url" value="jdbc:mysql://localhost:3306/yourdatabase" />
                    <spring:property name="username" value="yourUsername" />
                    <spring:property name="password" value="yourPassword" />
                </spring:bean>
            """);
        } else
            throw new RuntimeException("No datasource found - specifiy a DataSource bean in your Membrane configuration");
    }

    public DataSource getDatasource() {
        return datasource;
    }

    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }
}
