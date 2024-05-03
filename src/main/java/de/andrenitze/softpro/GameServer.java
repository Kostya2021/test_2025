package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.entities.LevelDecisions;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.Decision;
import de.andrenitze.softpro.types.DecisionDAO;
import de.andrenitze.softpro.types.EventType;
import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends WebSocketServer {
    public static final int MAX_PLAYER_NAME_LENGTH = 25;
    private final Set<Game> games = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WebSocket, Player> lobby = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Gson GSON = new Gson();
    private GameOverStats dailyHighScore;
    private static final String URL = "jdbc:mariadb://localhost:3306/thatsoftwaregame?user="+Config.getProperty("db.user")+"&password="+Config.getProperty("db.password");
    private DataSource dataSource;

    /**
     * Creates a GameServer instance to manage games and players
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));

        initiateDataSource();

        // Fetch high-score in a separate thread
        new Thread(this::fetchHighscore).start();

        // Find and close empty games regularly in a separate thread
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2500);
                    findAndCloseEmptyGames();
                } catch (InterruptedException e) {
                    logger.error("Error while sleeping: {}", e.getMessage());
                }
            }
        }).start();
    }

    private void initiateDataSource() {
        GenericObjectPool<PoolableConnection> connectionPool = GameServer.createObjectPool();
        dataSource = new PoolingDataSource(connectionPool);

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rset = stmt.executeQuery("SELECT * from Decisions")) {
                    int numcols = rset.getMetaData().getColumnCount();
                    while (rset.next()) {
                        connectionPoolStatus(connectionPool);
                        for (int i = 1; i <= numcols; i++) {
                            System.out.print("\t" + rset.getString(i));
                        }
                        System.out.println("");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        connectionPoolStatus(connectionPool);
    }

    public static GenericObjectPool<PoolableConnection> createObjectPool() {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(URL);

        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setValidationQuery("SELECT 1");

        GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        config.setTestOnBorrow(true);
        config.setMaxTotal(10);

        return new GenericObjectPool<>(poolableConnectionFactory, config);
    }

    private static void connectionPoolStatus(GenericObjectPool<PoolableConnection> connectionPool) {
        System.out.println(String.format("Active: %s; Idle  : %s", connectionPool.getNumActive(), connectionPool.getNumIdle()));
    }

    private void fetchHighscore() {
        logger.info("Fetching high-score from database");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            Class.forName(Config.getProperty("db.driver"));
            conn = DriverManager.getConnection(Config.getProperty("db.url"), Config.getProperty("db.user"), Config.getProperty("db.password"));

            String sql = "SELECT * FROM GameOverStats WHERE DATE(finishedAt) = CURDATE() ORDER BY projectsVolume DESC LIMIT 1";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            if (rs.next()) {
                int projectsVolume = rs.getInt("projectsVolume");
                this.dailyHighScore = getCurrentHighScore();
                assert this.dailyHighScore != null;
                this.dailyHighScore.setProjectsVolume(projectsVolume);

                if (this.dailyHighScore.getProjectsVolume() > 0) {
                    logger.info("Current high-score ({} € volume) fetched from database", this.dailyHighScore.getProjectsVolume());
                } else {
                    logger.info("No high-score set for today, yet.");
                }
            }
        } catch (ClassNotFoundException e) {
            logger.warn("JDBC Driver not found: {}", e.getMessage());
        } catch (SQLException e) {
            logger.warn("Could not initialize database connection: {}", e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.warn("Error closing database resources: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
        // When a new WebSocket connection is opened, it's a player joining the lobby
        logger.info("Client {} connected", webSocket.getRemoteSocketAddress());

        // Send version number to frontend
        final Properties properties = new Properties();
        try {
            properties.load(getClass().getClassLoader().getResourceAsStream("project.properties"));
            String version = properties.getProperty("version");
            webSocket.send("{\"type\": \""+EventType.VERSION+"\", \"payload\": \""+version+"\"}");
        } catch (IOException e) {
            logger.error("Could not load project.properties file");
        }

        // Generate a new player
        Player newPlayer = new Player();
        lobby.put(webSocket, newPlayer);

        // Create a new game instance for this player and add her to it
        Game game = new Game(this);
        game.addPlayerToGame(webSocket, newPlayer);
        game.initializePlayers();
        games.add(game);

        // Return generated player to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(newPlayer);
        webSocket.send(GSON.toJson(playerUpdateEvent));

        broadcastLobbyState();

        logger.info("New player '{}' added. New number of players in lobby: {}",
                newPlayer.getName(),
                lobby.size());
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        removeDisconnectedClient(webSocket);
    }

    private void removeDisconnectedClient(WebSocket webSocket) {
        // Remove disconnected clients from lobby
        logger.debug("Removing WebSocket {} from lobby and game", webSocket.getRemoteSocketAddress());
        Player player = lobby.remove(webSocket);
        if (player != null) {
            logger.info("Player '{}' disconnected. New number of players in lobby: {}",
                    player.getName(),
                    lobby.size());
        }

        // Remove disconnected client from running game
        for (Game game : games) {
            if (game.hasWebSocket(webSocket)) {
                game.removePlayerFromGame(webSocket);

                int numberOfPlayers = game.getPlayers().size();
                logger.debug("A player left the game - {} player(s) left in the game", numberOfPlayers);

                if (game.closeIfEmpty()) {
                    // Remove all references to the game
                    if (games.remove(game)) {
                        logger.info("Game closed. {} game(s) left", games.size());
                    } else {
                        logger.error("Could not remove game from games set");
                    }
                }

                // Don't search any further
                break;
            }
        }
        broadcastLobbyState();
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        logger.debug("received message from {}: {}", webSocket.getRemoteSocketAddress(), message);

        // Handle lobby events (PLAYER_READY, PLAYER_NAME_UPDATED) here and forward everything else to the game instances
        try {
            GameEvent genericGameEvent = GSON.fromJson(message, GameEvent.class);

            // If player is ready to play, make her available to be picked up by game instances.
            if (EventType.PLAYER_READY.equals(genericGameEvent.getType())) {
                try {
                    Type payloadType = new TypeToken<GameEvent<LevelDecisions>>() {}.getType();
                    GameEvent<LevelDecisions> gameEvent = GSON.fromJson(message, payloadType);

                    List<Decision> decisions = gameEvent.getPayload().getDecisions();
                    int level = gameEvent.getPayload().getLevel();

                    Player player = lobby.get(webSocket);
                    lobby.get(webSocket).setDecisions(level, decisions);
                    player.setReady(true);

                    broadcastLobbyState();

                    // Persist player decision(s) to database
                    DecisionDAO decisionDao = new DecisionDAO(dataSource);
                    try {
                        decisionDao.saveDecisions(player.getId().toString(), level, decisions);
                    } catch (SQLException e) {
                        logger.error("Could not persist player decisions to database: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    logger.debug(e.getMessage());
                    logger.error("Websocket message was malformed!");
                }
            } else if (EventType.PLAYER_NAME_UPDATED.equals(genericGameEvent.getType())) {
                // Allow name changes in the lobby
                Type payloadType = new TypeToken<GameEvent<Player>>() {}.getType();
                GameEvent<Player> updatedPlayerEvent = GSON.fromJson(message, payloadType);
                Player updatedPlayer = updatedPlayerEvent.getPayload();

                // Sanitize string, but allow spaces, and special characters like é,ß,ä,ö,ü...
                String newName = updatedPlayer.getName();
                newName = newName.substring(0, Math.min(MAX_PLAYER_NAME_LENGTH, newName.length())).replaceAll("[^\\p{L}\\p{M}\\s]", "").trim();
                if (newName.length() >= 2) {
                    Player player = this.lobby.get(webSocket);
                    String oldName = player.getName();
                    player.setName(newName);

                    // Confirm successful name change
                    GameEvent<Player> playerUpdateEvent = new GameEvent<>();
                    playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
                    playerUpdateEvent.setPayload(player);
                    webSocket.send(GSON.toJson(playerUpdateEvent));

                    // Notify everyone in the lobby
                    broadcastLobbyState();
                    logger.info("{} changed name to {}", oldName, newName);
                }
            } else {
                // Forward all other events to the corresponding game instance
                // Find out which game the message belongs to by its Websocket connection
                // WARNING This is on the critical path, so look for performance issues!
                for (Game game : games) {
                    if (game.hasWebSocket(webSocket)) {
                        game.getEventHandler().handleEvent(webSocket, message);
                    }
                }
            }

            // Start game sessions for all ready players in the game lobby
            for (Map.Entry<WebSocket, Player> player : lobby.entrySet()) {
                if (player.getValue().isReady()) {
                    // Remove player from lobby
                    lobby.remove(player.getKey());

                    // Find corresponding game instance for this player
                    Game game = games.stream()
                            .filter(g -> g.hasWebSocket(player.getKey()))
                            .findFirst()
                            .orElse(null);

                    if (game == null) {
                        logger.error("Could not find game instance for player {}", player.getValue().getName());
                        return;
                    }

                    game.addPlayerToGame(player.getKey(), player.getValue());
                    game.start();

                    logger.info("Players in the lobby: {} | Running games: {}", lobby.size(), games.size());
                    broadcastLobbyState();
                }
            }
        } catch (JSONException | JsonSyntaxException e) {
            logger.error("Received invalid websocket message: {}", e.getMessage());
        }
    }

    protected void broadcastLobbyState() {
        JSONArray playersList = new JSONArray();
        for (Map.Entry<WebSocket, Player> entry : lobby.entrySet()) {
            Player readyPlayer = entry.getValue();
            JSONObject player = new JSONObject();
            player.put("id", readyPlayer.getId().toString());
            player.put("name", readyPlayer.getName());
            player.put("isReady", readyPlayer.isReady());
            playersList.put(player);
        }

        // Anonymize highscore before sending
        GameOverStats anonymizedHighScore = new GameOverStats();
        if (dailyHighScore != null && dailyHighScore.getDeliveredProjects() > 0) {
            anonymizedHighScore.setPlayerName(dailyHighScore.getPlayerName());
            anonymizedHighScore.setDeliveredProjects(dailyHighScore.getDeliveredProjects());
            anonymizedHighScore.setProjectsVolume(dailyHighScore.getProjectsVolume());
            anonymizedHighScore.setFinishedAt(dailyHighScore.getFinishedAt());
            anonymizedHighScore.setSurvivedDays(dailyHighScore.getSurvivedDays());
        }

        broadcast("{\"type\": \""+EventType.UPDATE_LOBBY+"\", \"payload\": { " +
                "\"runningGames\": " + games.size() +
                ", \"players\": " + playersList +
                ", \"highscore\": " + GSON.toJson(anonymizedHighScore) + "}}");
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteBuffer message) {
        logger.debug("received ByteBuffer from {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket webSocket, Exception ex) {
        // Most likely a player dropped out of the game and the WebSocket connection is gone
        if (webSocket != null) {
            logger.warn("Connection {} was closed unexpectedly.", webSocket.getRemoteSocketAddress());
            // Kick player and close game if empty
            removeDisconnectedClient(webSocket);
        } else {
            logger.warn("An error occurred on a connection. {}", (Object) ex.getStackTrace());
        }
    }

    @Override
    public void onStart() {
        logger.info("Server started successfully");
    }

    void addPlayerToLobby(WebSocket webSocket, Player player) {
        lobby.put(webSocket, player);
        broadcastLobbyState();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    @Nullable
    GameOverStats getCurrentHighScore() {
        GameOverStats highScore = null;
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            Class.forName(Config.getProperty("db.driver"));
            conn = DriverManager.getConnection(Config.getProperty("db.url"), Config.getProperty("db.user"), Config.getProperty("db.password"));

            String sql = "SELECT * FROM GameOverStats WHERE DATE(finishedAt) = CURRENT_DATE ORDER BY projectsVolume DESC LIMIT 1";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            if (rs.next()) {
                highScore = new GameOverStats();
                highScore.setId(rs.getInt("id"));
                highScore.setDeliveredProjects(rs.getInt("deliveredProjects"));
                highScore.setProjectsVolume(rs.getInt("projectsVolume"));
                highScore.setReport(rs.getString("report"));
                highScore.setPlayerName(rs.getString("playerName"));
                highScore.setIpAddress(rs.getString("ipAddress"));
                highScore.setFinishedAt(rs.getTimestamp("finishedAt"));
                highScore.setGameId(rs.getString("gameId"));
                highScore.setSurvivedDays(rs.getInt("survivedDays"));
                highScore.setPlayedSeconds(rs.getInt("playedSeconds"));
            }
        } catch (ClassNotFoundException e) {
            logger.warn("JDBC Driver not found: {}", e.getMessage());
            return null;
        } catch (SQLException e) {
            logger.warn("Could not fetch high-score from database: {}", e.getMessage());
            return null;
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.warn("Error closing database resources: {}", e.getMessage());
            }
        }
        return highScore;
    }

    private void findAndCloseEmptyGames() {
        if (!games.isEmpty()) {
            for (Game game : games) {
                if (game.getPlayers().isEmpty()) {
                    logger.debug("Found empty game! Closing...");
                    games.remove(game);
                    broadcastLobbyState();
                }
            }
        }
    }
}