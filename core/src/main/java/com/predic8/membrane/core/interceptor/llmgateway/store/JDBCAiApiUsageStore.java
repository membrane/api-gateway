/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.store;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.jdbc.AbstractJdbcSupport;

import java.sql.SQLException;

/**
 * @description Records the token usage of every request in a database table (experimental). It neither
 * authenticates clients nor enforces token limits, so use it where the gateway should only account for
 * what was used.
 */
@MCElement(name = "jdbcAiApiUsageStore")
public class JDBCAiApiUsageStore extends AbstractJdbcSupport implements AiApiStore {

    private static final String UNKNOWN_USER = "anonymous";

    // @TODO  GENERATED ALWAYS AS IDENTITY  is PostgreSQL specific
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS ai_api_usage (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, 
                username VARCHAR(255) NOT NULL,
                input_tokens INT NOT NULL,
                output_tokens INT NOT NULL,
                total_tokens INT NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO ai_api_usage (
                username,
                input_tokens,
                output_tokens,
                total_tokens
            ) VALUES (?, ?, ?, ?)
            """;

    @Override
    public void init(Router router) {
        super.init(router);
        createTablesIfNotExist();
    }

    @Override
    public void store(AiApiUser user, com.predic8.membrane.core.interceptor.llmgateway.store.Usage usage) {
        try (var connection = getConnection(); var ps = connection.prepareStatement(INSERT_SQL)) {
            ps.setString(1, usernameOf(user));
            ps.setInt(2, usage.inputTokens());
            ps.setInt(3, usage.outputTokens());
            ps.setInt(4, usage.totalTokens());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not store AI API usage.", e);
        }
    }

    /**
     * The store does not authenticate, so there is no user to attribute the usage to unless one is
     * already on the exchange, while the username column is NOT NULL.
     */
    private static String usernameOf(AiApiUser user) {
        if (user == null || user.getName() == null)
            return UNKNOWN_USER;
        return user.getName();
    }

    private void createTablesIfNotExist() {
        try (var connection = getConnection(); var ps = connection.prepareStatement(CREATE_TABLE_SQL)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not create AI API usage table.", e);
        }
    }
}