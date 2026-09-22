package ru.repairradar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import ru.repairradar.exception.RepairOperationConflictException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RepairOperationGuard {

    // Session lock is held across HTTP calls without an open database transaction.
    private static final long LOCK_KEY = 827349120481L;
    private final DataSource dataSource;

    public Lease acquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            if (!lock(connection, "SELECT pg_try_advisory_lock(?)")) {
                connection.close();
                throw new RepairOperationConflictException();
            }
            return new Lease(connection);
        } catch (SQLException e) {
            closeFailedConnection(connection, e);
            throw new DataAccessResourceFailureException("Cannot acquire repair operation lock", e);
        }
    }

    private static boolean lock(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, LOCK_KEY);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static void closeFailedConnection(Connection connection, Exception failure) {
        if (connection != null) {
            try {
                connection.abort(Runnable::run);
                connection.close();
            } catch (SQLException e) {
                failure.addSuppressed(e);
            }
        }
    }

    public static final class Lease implements AutoCloseable {

        private final Connection connection;

        private Lease(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            try {
                lock(connection, "SELECT pg_advisory_unlock(?)");
                connection.close();
            } catch (SQLException e) {
                closeFailedConnection(connection, e);
                log.error("Failed to release repair operation lock", e);
            }
        }
    }
}
