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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.router.*;
import org.slf4j.*;

import javax.sql.*;
import java.sql.*;
import java.util.*;

import static com.predic8.membrane.core.util.SecurityUtils.*;

@MCElement(name = "jdbcUserDataProvider")
public class JdbcUserDataProvider implements UserDataProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserDataProvider.class);

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
            createTableIfNeeded(); // @todo: works with postgres but prints stacktrace and warning
        } catch (SQLException e) {
            log.warn("Error creating table.", e);
        }
    }

    /**
     * As we can't use prepared statement parameters for table- and columnnames so we have to sanitize the user input here.
     */
    private void sanitizeUserInputs() {
        if (tableName == null || userColumnName == null || passwordColumnName == null)
            throw new IllegalArgumentException("Table and column names must be set.");

        String identifierPattern = "^[A-Za-z0-9_]+$";
        if (!tableName.matches(identifierPattern)
            || !userColumnName.matches(identifierPattern)
            || !passwordColumnName.matches(identifierPattern)) {
            throw new IllegalArgumentException("Table/column names must be alphanumeric/underscore.");
        }
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
        if (router.getRegistry() != null) {
            var ds = router.getRegistry().getBean(DataSource.class);
            if (ds.isPresent()) {
                datasource = ds.get();
                return;
            }
        }
        if (router.getBeanFactory() != null) {
            Map<String, DataSource> beans = router.getBeanFactory().getBeansOfType(DataSource.class);
            DataSource[] datasources = beans.values().toArray(new DataSource[0]);
            if (datasources.length > 0) {
                datasource = datasources[0];
                return;
            }
        }
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

        Map<String, String> result = new HashMap<>();
        try (var con = datasource.getConnection();
             var preparedStatement = con.prepareStatement(createGetUsersSql())) {
            preparedStatement.setString(1, username);
            try (var rs = preparedStatement.executeQuery()) {
                var rsmd = rs.getMetaData();
                while (rs.next()) {
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        var value = rs.getObject(i);
                        if (value != null) {
                            result.put(rsmd.getColumnName(i).toLowerCase(), value.toString());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }

        if (!result.isEmpty()) {
            String passwordFromDB = result.get(getPasswordColumnName().toLowerCase());
            if (!isHashedPassword(password) && isHashedPassword(passwordFromDB))
                password = createPasswdCompatibleHash(new AlgoSalt(extractMagicString(passwordFromDB), extractSalt(passwordFromDB)), password);
            if (username.equals(result.get(getUserColumnName().toLowerCase())) && password.equals(passwordFromDB))
                return result;
        }

        throw new NoSuchElementException();
    }

    private String createGetUsersSql() {
        return "SELECT * FROM %s WHERE %s=?".formatted(getTableName(), getUserColumnName());
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