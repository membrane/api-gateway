/*
 * Copyright 2017 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@MCElement(name="customStatementJdbcUserDataProvider")
public class CustomStatementJdbcUserDataProvider implements  UserDataProvider {
    private static Logger log = LoggerFactory.getLogger(CustomStatementJdbcUserDataProvider.class.getName());

    private Router router;

    DataSource datasource;

    String tableName;
    String userColumnName;
    String passwordColumnName;


    @Override
    public void init(Router router) {
        this.router = router;
        sanitizeUserInputs();
        getDatasourceIfNull();
    }

    private void sanitizeUserInputs() {
        // As we can't use prepared statement parameters for table- and columnnames so we have to sanitize the user input here.
        // After this method it is assumed that user input is save to use

        // TODO sanitize inputs here
    }

    private void getDatasourceIfNull() {
        if(datasource != null)
            return;

        Map<String, DataSource> beans = router.getBeanFactory().getBeansOfType(DataSource.class);

        DataSource[] datasources = beans.values().toArray(new DataSource[0]);
        if(datasources.length > 0)
            datasource = datasources[0];
        else
            throw new RuntimeException("No datasource found - specifiy a DataSource bean in your Membrane configuration");
    }

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        String username = postData.get("username");
        if (username == null)
            throw new NoSuchElementException();

        String password = postData.get("password");
        if (password == null)
            throw new NoSuchElementException();

        Connection con = null;
        PreparedStatement preparedStatement = null;
        try{
            con = datasource.getConnection();
            preparedStatement = con.prepareStatement(checkPasswordSql);
            preparedStatement.setString(1,username);
            preparedStatement.setString(2, password);

            ResultSet rs = preparedStatement.executeQuery();
            ResultSetMetaData rsmd = rs.getMetaData();

            HashMap<String, String> result = new HashMap<String, String>();
            if (!rs.next())
                throw new NoSuchElementException();
            result.put(sqlResultAttribute, rs.getObject(1).toString());


            if(preparedStatement != null)
                preparedStatement.close();
            if(con != null)
                con.close();

            result.put(userNameAttribute, username);
            return result;

        } catch (SQLException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

        throw new NoSuchElementException();
    }

    private String checkPasswordSql;

    private String sqlResultAttribute;
    private String userNameAttribute;

    public String getCheckPasswordSql() {
        return checkPasswordSql;
    }

    @MCAttribute
    public void setCheckPasswordSql(String checkPasswordSql) {
        this.checkPasswordSql = checkPasswordSql;
    }

    public String getSqlResultAttribute() {
        return sqlResultAttribute;
    }

    @MCAttribute
    public void setSqlResultAttribute(String sqlResultAttribute) {
        this.sqlResultAttribute = sqlResultAttribute;
    }

    public String getUserNameAttribute() {
        return userNameAttribute;
    }

    @MCAttribute
    public void setUserNameAttribute(String userNameAttribute) {
        this.userNameAttribute = userNameAttribute;
    }

    public DataSource getDatasource() {
        return datasource;
    }

    @MCAttribute
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public String getTableName() {
        return tableName;
    }

    @MCAttribute
    @Required
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getUserColumnName() {
        return userColumnName;
    }

    @MCAttribute
    @Required
    public void setUserColumnName(String userColumnName) {
        this.userColumnName = userColumnName;
    }

    public String getPasswordColumnName() {
        return passwordColumnName;
    }

    @MCAttribute
    @Required
    public void setPasswordColumnName(String passwordColumnName) {
        this.passwordColumnName = passwordColumnName;
    }

}
