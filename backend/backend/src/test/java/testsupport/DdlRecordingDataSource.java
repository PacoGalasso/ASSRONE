package testsupport;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Wraps a real DataSource so every SQL string actually handed to the JDBC
 * driver is recorded — via createStatement/prepareStatement/prepareCall and
 * their execute*(String) overloads — regardless of which subsystem
 * (Flyway's callback, a Flyway migration, or Hibernate) issued it. Schema
 * DDL executed by Hibernate's SchemaValidator/SchemaCreator talks straight
 * to the JDBC Connection, bypassing Hibernate's own StatementInspector hook
 * entirely, so that hook can't be used to prove "Hibernate issued no DDL" —
 * this wrapper sits one layer lower, where nothing can be missed.
 *
 * markFlywayComplete() records a boundary index once Flyway's own
 * AFTER_MIGRATE event fires (see the test's own Callback bean); everything
 * recorded from that index onward was issued while Hibernate was building
 * and validating its SessionFactory, which is exactly the window a
 * ddl-auto=validate regression would show up in.
 */
public class DdlRecordingDataSource implements DataSource {

    private static final List<String> RECORDED_SQL = new CopyOnWriteArrayList<>();
    private static final AtomicInteger FLYWAY_COMPLETE_AT_INDEX = new AtomicInteger(-1);

    private final DataSource delegate;

    public DdlRecordingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    public static void reset() {
        RECORDED_SQL.clear();
        FLYWAY_COMPLETE_AT_INDEX.set(-1);
    }

    public static void markFlywayComplete() {
        FLYWAY_COMPLETE_AT_INDEX.compareAndSet(-1, RECORDED_SQL.size());
    }

    /**
     * SQL recorded after Flyway's own AFTER_MIGRATE event — i.e. everything
     * issued while Hibernate was building/validating its schema.
     */
    public static List<String> sqlRecordedAfterFlywayCompleted() {
        int boundary = FLYWAY_COMPLETE_AT_INDEX.get();
        if (boundary < 0) {
            throw new IllegalStateException("markFlywayComplete() was never called — Flyway's AFTER_MIGRATE callback did not fire.");
        }
        return Collections.unmodifiableList(RECORDED_SQL.subList(boundary, RECORDED_SQL.size()));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    private static Connection wrapConnection(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                DdlRecordingDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new RecordingHandler(real));
    }

    private static Object wrapStatement(Object real, Class<?> iface) {
        return Proxy.newProxyInstance(
                DdlRecordingDataSource.class.getClassLoader(),
                new Class<?>[]{iface},
                new RecordingHandler(real));
    }

    private static final class RecordingHandler implements InvocationHandler {
        private final Object target;

        private RecordingHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if (args != null && args.length > 0 && args[0] instanceof String sql
                        && (name.equals("execute") || name.equals("executeUpdate") || name.equals("executeQuery")
                        || name.equals("createStatement") || name.equals("prepareStatement") || name.equals("prepareCall")
                        || name.equals("addBatch"))) {
                    RECORDED_SQL.add(sql);
                }
                Object result = method.invoke(target, args);
                return switch (name) {
                    case "createStatement" -> wrapStatement(result, Statement.class);
                    case "prepareStatement" -> wrapStatement(result, PreparedStatement.class);
                    case "prepareCall" -> wrapStatement(result, CallableStatement.class);
                    default -> result;
                };
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
