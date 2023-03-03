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
import com.predic8.membrane.core.interceptor.registration.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.predic8.membrane.annot.Required;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@MCElement(name = "jdbcUserDataProvider")
public class JdbcUserDataProvider implements UserDataProvider {
    private static final Logger log = LoggerFactory.getLogger(JdbcUserDataProvider.class.getName());
    private DataSource datasource;
    private String tableName;
    private String userColumnName;
    private String passwordColumnName;
    private Router router;

    @Override
    public void init(Router router) {
        this.router = router;

        sanitizeUserInputs();
        getDatasourceIfNull();

        try {
            createTableIfNeeded();
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Something went wrong at jdbcUserDataProvider table creation");
            log.error(e.getMessage());
        }
    }

    private void sanitizeUserInputs() {
        // As we can't use prepared statement parameters for table- and columnnames so we have to sanitize the user input here.
        // After this method it is assumed that user input is save to use

        // TODO sanitize inputs here
    }

    private void createTableIfNeeded() throws SQLException {

        try (Connection con = datasource.getConnection(); Statement statement = con.createStatement()) {
            statement.executeUpdate(getCreateTableSql());
        }
    }

    private String getCreateTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + getTableName() + "(" +
                "id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
                getUserColumnName() + " varchar NOT NULL, " +
                getPasswordColumnName() + " varchar NOT NULL, " +
                "verified boolean NOT NULL DEFAULT false" +
                ");";
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
        HashMap<String, String> result = null;
        try {
            con = datasource.getConnection();
            preparedStatement = con.prepareStatement(createGetUsersSql());
            preparedStatement.setString(1, username);

            ResultSet rs = preparedStatement.executeQuery();
            ResultSetMetaData rsmd = rs.getMetaData();

            result = new HashMap<>();
            while (rs.next()) for (int i = 1; i <= rsmd.getColumnCount(); i++)
                result.put(rsmd.getColumnName(i).toLowerCase(), rs.getObject(i).toString());

            rs.close();
            preparedStatement.close();
            con.close();

        } catch (SQLException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

        if (result != null && result.size() > 0) {
            String passwordFromDB = result.get(getPasswordColumnName().toLowerCase());
            if (!SecurityUtils.isHashedPassword(password))
                password = SecurityUtils.createPasswdCompatibleHash(password, SecurityUtils.extractSalt(passwordFromDB));

            if (username.equals(result.get(getUserColumnName().toLowerCase())) && password.equals(passwordFromDB))
                return result;
        }

        throw new NoSuchElementException();
    }

    private String createGetUsersSql() {
        return "SELECT * FROM " + getTableName() +
                " WHERE " + getUserColumnName() + "=?";
    }

    public DataSource getDatasource() {
        return datasource;
    }

    @MCAttribute
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public String getTableName() {
        return tableName.toUpperCase();
    }

    @MCAttribute
    @Required
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getUserColumnName() {
        return userColumnName.toUpperCase();
    }

    @MCAttribute
    @Required
    public void setUserColumnName(String userColumnName) {
        this.userColumnName = userColumnName;
    }

    public String getPasswordColumnName() {
        return passwordColumnName.toUpperCase();
    }

    @MCAttribute
    @Required
    public void setPasswordColumnName(String passwordColumnName) {
        this.passwordColumnName = passwordColumnName;
    }

}
