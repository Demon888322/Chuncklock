package com.chunklock.storage;

import com.chunklock.model.ChunkClaim;
import com.chunklock.model.ChunkKey;
import com.chunklock.model.ClaimFlag;
import com.chunklock.model.PlayerProgress;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ClaimRepository implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ExecutorService databaseExecutor;
    private volatile HikariDataSource dataSource;

    public ClaimRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ChunkLock-Database");
            thread.setDaemon(true);
            return thread;
        };
        this.databaseExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public CompletableFuture<StoredData> initializeAndLoad() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                openDataSource();
                createSchema();
                return new StoredData(loadClaims(), loadPlayerProgress());
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> save(ChunkClaim claim) {
        ChunkClaim snapshot = claim.copy();
        return CompletableFuture.runAsync(() -> {
            try {
                saveClaim(snapshot);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> delete(ChunkKey key) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = requireDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    deleteRows(connection, "chunk_claim_flags", key);
                    deleteRows(connection, "chunk_claim_members", key);
                    deleteRows(connection, "chunk_claims", key);
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveProgress(PlayerProgress progress) {
        PlayerProgress snapshot = progress.snapshot();
        return CompletableFuture.runAsync(() -> {
            try {
                saveProgressSnapshot(snapshot);
                progress.markPersisted(snapshot.revision());
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    private void openDataSource() throws SQLException {
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();
        HikariConfig config = new HikariConfig();
        config.setPoolName("ChunkLock-Pool");
        config.setMaximumPoolSize(Math.max(1, plugin.getConfig().getInt("storage.pool.maximum-size", 4)));
        config.setConnectionTimeout(Math.max(1000L, plugin.getConfig().getLong("storage.pool.connection-timeout-ms", 10000L)));

        if (type.equals("sqlite")) {
            String fileName = plugin.getConfig().getString("storage.sqlite.file", "claims.db");
            File file = new File(plugin.getDataFolder(), fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("Could not create database directory: " + parent);
            }
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        } else if (type.equals("mysql")) {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "chunklock");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + plugin.getConfig().getBoolean("storage.mysql.use-ssl", false)
                    + "&serverTimezone=UTC");
            config.setUsername(plugin.getConfig().getString("storage.mysql.username", "chunklock"));
            config.setPassword(plugin.getConfig().getString("storage.mysql.password", ""));
        } else {
            throw new SQLException("Unsupported storage.type: " + type + " (expected sqlite or mysql)");
        }

        dataSource = new HikariDataSource(config);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunk_claims (
                      world_id VARCHAR(36) NOT NULL,
                      chunk_x INTEGER NOT NULL,
                      chunk_z INTEGER NOT NULL,
                      owner_id VARCHAR(36) NOT NULL,
                      created_at BIGINT NOT NULL,
                      last_owner_login BIGINT NOT NULL,
                      PRIMARY KEY (world_id, chunk_x, chunk_z)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunk_claim_members (
                      world_id VARCHAR(36) NOT NULL,
                      chunk_x INTEGER NOT NULL,
                      chunk_z INTEGER NOT NULL,
                      member_id VARCHAR(36) NOT NULL,
                      PRIMARY KEY (world_id, chunk_x, chunk_z, member_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunk_claim_flags (
                      world_id VARCHAR(36) NOT NULL,
                      chunk_x INTEGER NOT NULL,
                      chunk_z INTEGER NOT NULL,
                      flag_name VARCHAR(32) NOT NULL,
                      flag_value INTEGER NOT NULL,
                      PRIMARY KEY (world_id, chunk_x, chunk_z, flag_name)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunk_player_progress (
                      player_id VARCHAR(36) NOT NULL PRIMARY KEY,
                      unlocked_claims INTEGER NOT NULL,
                      task_progress BIGINT NOT NULL
                    )
                    """);
        }
    }

    private List<PlayerProgress> loadPlayerProgress() throws SQLException {
        List<PlayerProgress> progress = new ArrayList<>();
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM chunk_player_progress")) {
            while (rows.next()) {
                progress.add(new PlayerProgress(UUID.fromString(rows.getString("player_id")),
                        rows.getInt("unlocked_claims"), rows.getLong("task_progress")));
            }
        }
        return progress;
    }

    private void saveProgressSnapshot(PlayerProgress progress) throws SQLException {
        try (Connection connection = requireDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE chunk_player_progress
                        SET unlocked_claims = ?, task_progress = ?
                        WHERE player_id = ?
                        """)) {
                    statement.setInt(1, progress.unlockedClaims());
                    statement.setLong(2, progress.taskProgress());
                    statement.setString(3, progress.playerId().toString());
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO chunk_player_progress
                            (player_id, unlocked_claims, task_progress)
                            VALUES (?, ?, ?)
                            """)) {
                        statement.setString(1, progress.playerId().toString());
                        statement.setInt(2, progress.unlockedClaims());
                        statement.setLong(3, progress.taskProgress());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private List<ChunkClaim> loadClaims() throws SQLException {
        Map<ChunkKey, LoadedClaim> loaded = new HashMap<>();
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM chunk_claims")) {
            while (rows.next()) {
                ChunkKey key = new ChunkKey(UUID.fromString(rows.getString("world_id")),
                        rows.getInt("chunk_x"), rows.getInt("chunk_z"));
                loaded.put(key, new LoadedClaim(key, UUID.fromString(rows.getString("owner_id")),
                        rows.getLong("created_at"), rows.getLong("last_owner_login")));
            }
        }
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM chunk_claim_members")) {
            while (rows.next()) {
                ChunkKey key = keyFrom(rows);
                LoadedClaim claim = loaded.get(key);
                if (claim != null) {
                    claim.members.add(UUID.fromString(rows.getString("member_id")));
                }
            }
        }
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM chunk_claim_flags")) {
            while (rows.next()) {
                ChunkKey key = keyFrom(rows);
                LoadedClaim claim = loaded.get(key);
                if (claim != null) {
                    try {
                        claim.flags.put(ClaimFlag.valueOf(rows.getString("flag_name")),
                                rows.getInt("flag_value") != 0);
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Ignoring unknown stored claim flag "
                                + rows.getString("flag_name") + " for " + key);
                    }
                }
            }
        }

        List<ChunkClaim> claims = new ArrayList<>(loaded.size());
        for (LoadedClaim loadedClaim : loaded.values()) {
            claims.add(new ChunkClaim(loadedClaim.key, loadedClaim.owner, loadedClaim.members,
                    loadedClaim.flags, loadedClaim.createdAt, loadedClaim.lastOwnerLogin));
        }
        return claims;
    }

    private void saveClaim(ChunkClaim claim) throws SQLException {
        ChunkKey key = claim.key();
        try (Connection connection = requireDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE chunk_claims SET owner_id = ?, created_at = ?, last_owner_login = ?
                        WHERE world_id = ? AND chunk_x = ? AND chunk_z = ?
                        """)) {
                    statement.setString(1, claim.owner().toString());
                    statement.setLong(2, claim.createdTimestamp());
                    statement.setLong(3, claim.lastOwnerLogin());
                    bindKey(statement, 4, key);
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO chunk_claims
                            (world_id, chunk_x, chunk_z, owner_id, created_at, last_owner_login)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)) {
                        bindKey(statement, 1, key);
                        statement.setString(4, claim.owner().toString());
                        statement.setLong(5, claim.createdTimestamp());
                        statement.setLong(6, claim.lastOwnerLogin());
                        statement.executeUpdate();
                    }
                }

                deleteRows(connection, "chunk_claim_members", key);
                deleteRows(connection, "chunk_claim_flags", key);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO chunk_claim_members (world_id, chunk_x, chunk_z, member_id)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    for (UUID member : claim.trustedMembers()) {
                        bindKey(statement, 1, key);
                        statement.setString(4, member.toString());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO chunk_claim_flags
                        (world_id, chunk_x, chunk_z, flag_name, flag_value)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    for (ClaimFlag flag : ClaimFlag.values()) {
                        bindKey(statement, 1, key);
                        statement.setString(4, flag.name());
                        statement.setInt(5, claim.hasFlag(flag) ? 1 : 0);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static ChunkKey keyFrom(ResultSet rows) throws SQLException {
        return new ChunkKey(UUID.fromString(rows.getString("world_id")),
                rows.getInt("chunk_x"), rows.getInt("chunk_z"));
    }

    private static void bindKey(PreparedStatement statement, int index, ChunkKey key) throws SQLException {
        statement.setString(index, key.worldId().toString());
        statement.setInt(index + 1, key.x());
        statement.setInt(index + 2, key.z());
    }

    private static void deleteRows(Connection connection, String table, ChunkKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE world_id = ? AND chunk_x = ? AND chunk_z = ?")) {
            bindKey(statement, 1, key);
            statement.executeUpdate();
        }
    }

    private HikariDataSource requireDataSource() throws SQLException {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) {
            throw new SQLException("Database connection pool is not initialized");
        }
        return current;
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
                plugin.getLogger().warning("Timed out waiting for claim database operations to finish.");
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while waiting for claim database operations to finish.");
        }
        HikariDataSource current = dataSource;
        if (current != null) {
            current.close();
        }
    }

    private static final class LoadedClaim {
        private final ChunkKey key;
        private final UUID owner;
        private final long createdAt;
        private final long lastOwnerLogin;
        private final Set<UUID> members = new HashSet<>();
        private final Map<ClaimFlag, Boolean> flags = new EnumMap<>(ClaimFlag.class);

        private LoadedClaim(ChunkKey key, UUID owner, long createdAt, long lastOwnerLogin) {
            this.key = key;
            this.owner = owner;
            this.createdAt = createdAt;
            this.lastOwnerLogin = lastOwnerLogin;
        }
    }
}
