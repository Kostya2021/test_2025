package de.andrenitze.softpro.config;

import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static de.andrenitze.softpro.Main.logger;

public final class DatabaseConfig {
    private static final AtomicReference<DataSource> dataSource = new AtomicReference<>();

    private DatabaseConfig() {
        // private constructor to prevent instantiation
    }

    public static DataSource getDataSource() {
        if (dataSource.get() == null) {
            synchronized (DatabaseConfig.class) {
                if (dataSource.get() == null) {
                    dataSource.set(createDataSource());
                }
            }
        }
        return dataSource.get();
    }

    private static DataSource createDataSource() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.debug("MySQL JDBC Driver successfully registered.");
            java.sql.Driver driver = new com.mysql.cj.jdbc.Driver();
            logger.debug("MySQL Connector/J Driver Version: {}", driver.getMajorVersion() + "." + driver.getMinorVersion());
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found! Please check if the driver JAR is included in the classpath.", e);
            // Handle the exception, e.g., set a flag or notify the user
        } catch (SQLException e) {
            logger.error("SQL Exception occurred while registering MySQL JDBC Driver.", e);
            // Handle the exception, e.g., set a flag or notify the user
        }

        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(
                Config.getProperty("db.url"),
                Config.getProperty("db.user"),
                Config.getProperty("db.password")
        );

        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);

        GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        config.setTestOnBorrow(true);
        config.setMaxTotal(10);

        GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory, config);
        poolableConnectionFactory.setPool(connectionPool);
        poolableConnectionFactory.setValidationQuery("SELECT 1");
        logConnectionPoolStatus(connectionPool);  // Initial status

        return new PoolingDataSource<>(connectionPool);
    }

    private static void logConnectionPoolStatus(GenericObjectPool<PoolableConnection> connectionPool) {
        logger.debug("Connection Pool Status -- Active: {}; Idle: {}", connectionPool.getNumActive(), connectionPool.getNumIdle());
    }
}
