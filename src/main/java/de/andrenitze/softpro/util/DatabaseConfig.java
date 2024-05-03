package de.andrenitze.softpro.util;

import de.andrenitze.softpro.Config;
import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.sql.DataSource;

import static de.andrenitze.softpro.Main.logger;

public final class DatabaseConfig {
    private static final String URL = "jdbc:mariadb://localhost:3306/thatsoftwaregame";
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
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(URL, Config.getProperty("db.user"), Config.getProperty("db.password"));
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
        logger.debug(String.format("Connection Pool Status -- Active: %s; Idle: %s", connectionPool.getNumActive(), connectionPool.getNumIdle()));
    }
}
