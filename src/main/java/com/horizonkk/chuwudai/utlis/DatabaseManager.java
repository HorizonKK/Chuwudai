package com.horizonkk.chuwudai.utlis;

import com.horizonkk.chuwudai.Chuwudai;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {
    private static HikariDataSource dataSource;
    
    public static void initialize(String dbPath) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        
        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // SQLite特殊配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "false");
        
        dataSource = new HikariDataSource(config);
        
        // 初始化数据库表
        initializeDatabase();
    }
    
    private static void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS bags (
                id TEXT PRIMARY KEY,
                size INTEGER NOT NULL,
                data TEXT NOT NULL,
                last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void vacuum() {
        String sql = "VACUUM";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            Chuwudai.getInstance().getLogger().info("数据库已优化，空间已回收");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
