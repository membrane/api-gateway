package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.Router;

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
        if (datasources.length > 0)
            datasource = datasources[0];
        else
            throw new RuntimeException("No datasource found - specifiy a DataSource bean in your Membrane configuration");
    }

    public DataSource getDatasource() {
        return datasource;
    }

    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
