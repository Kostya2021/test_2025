package de.andrenitze.softpro.util;

import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.sql.DataSource;

import static de.andrenitze.softpro.Main.logger;

public final class DatabaseConfig {
    private static volatile DataSource dataSource;

    private DatabaseConfig() {
        // private constructor to prevent instantiation
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatabaseConfig.class) {
                if (dataSource == null) {
                    dataSource = createDataSource();
                }
            }
        }
        return dataSource;
    }

    private static DataSource createDataSource() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            logger.info("MySQL JDBC Driver successfully registered.");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found! Please check if the driver JAR is included in the classpath.", e);
            throw new RuntimeException("MySQL JDBC Driver not found.", e);
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
