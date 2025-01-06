package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class JdbcUserDataProvider extends AbstractJdbcUserDataProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserDataProvider.class.getName());
    private String userColumnName;
    private String passwordColumnName;

    @Override
    public void init(Router router) {
        super.init(router);
        sanitizeUserInputs();
        try {
            createTableIfNeeded();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error during table creation: ", e);
        }
    }

    private void sanitizeUserInputs() {
        // As we can't use prepared statement parameters for table- and columnnames so we have to sanitize the user input here.
        // After this method it is assumed that user input is save to use

        // TODO sanitize inputs here
    }

    private void createTableIfNeeded() throws SQLException {
        try (Connection con = getDatasource().getConnection(); Statement statement = con.createStatement()) {
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

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        String username = postData.get("username");
        if (username == null)
            throw new NoSuchElementException("Username not found");

        String password = postData.get("password");
        if (password == null)
            throw new NoSuchElementException("Password not found");

        HashMap<String, String> result = new HashMap<>();
        result.put("username", username);
        result.put("password", password);
        return result;
    }

    public String getUserColumnName() {
        return userColumnName.toUpperCase();
    }

    public void setUserColumnName(String userColumnName) {
        this.userColumnName = userColumnName;
    }

    public String getPasswordColumnName() {
        return passwordColumnName.toUpperCase();
    }

    public void setPasswordColumnName(String passwordColumnName) {
        this.passwordColumnName = passwordColumnName;
    }
}
