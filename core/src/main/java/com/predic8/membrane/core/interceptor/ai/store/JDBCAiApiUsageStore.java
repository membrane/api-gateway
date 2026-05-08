package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.jdbc.AbstractJdbcSupport;

import java.sql.SQLException;
import java.util.Optional;

@MCElement(name = "jdbcAiApiUsageStore")
public class JDBCAiApiUsageStore extends AbstractJdbcSupport implements AiApiStore {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS ai_api_usage (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, // @TODO  GENERATED ALWAYS AS IDENTITY  is PostgreSQL specific
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
    public void store(AiApiUser user, com.predic8.membrane.core.interceptor.ai.store.Usage usage) {
        try (var connection = getConnection(); var ps = connection.prepareStatement(INSERT_SQL)) {
            ps.setString(1, user.getName());
            ps.setInt(2, usage.inputTokens());
            ps.setInt(3, usage.outputTokens());
            ps.setInt(4, usage.totalTokens());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not store AI API usage.", e);
        }
    }

    @Override
    public Optional<AiApiUser> getUser(String token) {
        return Optional.empty();
    }

    @Override
    public long checkLimit(AiApiUser user) {
        return 0;
    }

    private void createTablesIfNotExist() {
        try (var connection = getConnection(); var ps = connection.prepareStatement(CREATE_TABLE_SQL)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not create AI API usage table.", e);
        }
    }
}