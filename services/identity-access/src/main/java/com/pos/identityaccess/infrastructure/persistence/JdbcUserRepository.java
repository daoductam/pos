package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.domain.model.UserStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String SELECT = """
            SELECT id, cognito_subject, normalized_email, display_name, phone, status,
                   reconciliation_status, created_at, updated_at
            FROM users
            """;

    private static final String INSERT = """
            INSERT INTO users (
                id, cognito_subject, normalized_email, display_name, phone, status,
                reconciliation_status, created_at, updated_at)
            VALUES (
                :id, :cognitoSubject, :normalizedEmail, :displayName, :phone, :status,
                :reconciliationStatus, :createdAt, :updatedAt)
            """;

    private static final String BIND_SUBJECT = """
            UPDATE users
            SET cognito_subject = :cognitoSubject,
                reconciliation_status = :reconciliationStatus,
                updated_at = now()
            WHERE id = :userId
              AND (cognito_subject IS NULL OR cognito_subject = :cognitoSubject)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<User> findByCognitoSubject(String cognitoSubject) {
        if (cognitoSubject == null || cognitoSubject.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(SELECT + " WHERE cognito_subject = :cognitoSubject",
                new MapSqlParameterSource().addValue("cognitoSubject", cognitoSubject),
                (resultSet, rowNum) -> new User(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("cognito_subject"),
                        resultSet.getString("normalized_email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("phone"),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("reconciliation_status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public Optional<User> findById(java.util.UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbc.query(SELECT + " WHERE id = :userId",
                new MapSqlParameterSource().addValue("userId", userId),
                (resultSet, rowNum) -> new User(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("cognito_subject"),
                        resultSet.getString("normalized_email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("phone"),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("reconciliation_status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(SELECT + " WHERE normalized_email = :normalizedEmail",
                new MapSqlParameterSource().addValue("normalizedEmail", normalizedEmail),
                (resultSet, rowNum) -> new User(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("cognito_subject"),
                        resultSet.getString("normalized_email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("phone"),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("reconciliation_status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()))
                .stream().findFirst();
    }

    @Override
    public User save(User user) {
        Objects.requireNonNull(user, "user");
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", user.id())
                .addValue("cognitoSubject", user.cognitoSubject())
                .addValue("normalizedEmail", user.normalizedEmail())
                .addValue("displayName", user.displayName())
                .addValue("phone", user.phone())
                .addValue("status", user.status().name())
                .addValue("reconciliationStatus", user.reconciliationStatus())
                .addValue("createdAt", user.createdAt())
                .addValue("updatedAt", user.updatedAt()));
        return user;
    }

    @Override
    public boolean bindCognitoSubject(java.util.UUID userId, String cognitoSubject,
                                      String reconciliationStatus) {
        Objects.requireNonNull(userId, "userId");
        if (cognitoSubject == null || cognitoSubject.isBlank()) {
            throw new IllegalArgumentException("cognitoSubject must not be blank");
        }
        if (reconciliationStatus == null || reconciliationStatus.isBlank()) {
            throw new IllegalArgumentException("reconciliationStatus must not be blank");
        }
        return jdbc.update(BIND_SUBJECT, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("cognitoSubject", cognitoSubject)
                .addValue("reconciliationStatus", reconciliationStatus)) == 1;
    }
}
