package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.EventType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.NativeQuery;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.NoResultException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameServer extends WebSocketServer {
    public static final int MAX_PLAYER_NAME_LENGTH = 25;
    private final Set<Game> games = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WebSocket, Player> lobby = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Gson GSON = new Gson();
    private GameOverStats dailyHighScore;
    public SessionFactory sessionFactory = null;

    /**
     * Creates a GameServer instance to manage games and players
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));

        fetchHighscore();
    }

    private void fetchHighscore() {
        // Initialize database session
        try {
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .configure("hibernate.cfg.xml")
                    .build();
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
            this.dailyHighScore = getCurrentHighScore();
            if (this.dailyHighScore != null && this.dailyHighScore.getProjectsVolume() > 0) {
                logger.info("Current high-score ({} € volume) fetched from database", this.dailyHighScore.getProjectsVolume());
            } else {
                logger.info("No high-score set for today, yet.");
            }
        } catch (Exception e) {
            logger.warn("Could not initialize database session: {}", e.getMessage());
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
        Player generatedPlayer = new Player();
        lobby.put(webSocket, generatedPlayer);

        // Return generated player to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(generatedPlayer);
        webSocket.send(GSON.toJson(playerUpdateEvent));

        broadcastLobbyState();

        logger.info("New player '{}' added. New number of players in lobby: {}",
                generatedPlayer.getName(),
                lobby.size());
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        logger.debug("WebSocket {} is closing? {}", webSocket.getRemoteSocketAddress(), webSocket.isClosing());
        removeDisconnectedClient(webSocket);
    }

    private void removeDisconnectedClient(WebSocket webSocket) {
        // Remove disconnected clients from lobby
        logger.debug("Removing WebSocket {} from lobby", webSocket.getRemoteSocketAddress());
        Player player = lobby.remove(webSocket);
        if (player != null) {
            logger.info("Player '{}' disconnected. New number of players in lobby: {}",
                    player.getName(),
                    lobby.size());
        }

        // Remove disconnected client from running game
        logger.debug("Removing WebSocket {} from game", webSocket.getRemoteSocketAddress());
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

        // Handle lobby events here and forward everything else to the game instances
        try {
            GameEvent<Object> genericGameEvent = GSON.fromJson(message, GameEvent.class);

            // If player is ready to play, add her to the lobby
            if (genericGameEvent.isOfType(EventType.PLAYER_READY)) {
                try {
                    //Type payloadType = new TypeToken<GameEvent<Player>>() {}.getType();
                    //GameEvent<Player> playerEvent = GSON.fromJson(message, payloadType);

                    Player player = lobby.get(webSocket);
                    player.setReady(true);
                    broadcastLobbyState();
                } catch (Exception e) {
                    logger.error("Websocket message was malformed!");
                }
            } else if (genericGameEvent.isOfType(EventType.PLAYER_NAME_UPDATED)) {
                // Allow name changes in the lobby
                Type payloadType = new TypeToken<GameEvent<Player>>() {}.getType();
                GameEvent<Player> updatedPlayerEvent = GSON.fromJson(message, payloadType);
                Player updatedPlayer = updatedPlayerEvent.getPayload();

                // Sanitize string, but allow spaces, and special characters like é,ß,ä,ö,ü...
                String newName = updatedPlayer.getName();
                newName = newName.substring(0, Math.min(MAX_PLAYER_NAME_LENGTH, newName.length())).replaceAll("[^\\p{L}\\p{M}\\s]", "").trim();
                if (newName.length() >= 2) {
                    Player player = this.lobby.get(webSocket);
                    player.setName(newName);

                    // Confirm successful name change
                    GameEvent<Player> playerUpdateEvent = new GameEvent<>();
                    playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
                    playerUpdateEvent.setPayload(player);
                    webSocket.send(GSON.toJson(playerUpdateEvent));

                    // Notify everyone in the lobby
                    broadcastLobbyState();
                    logger.info("{} changed name to {}", player.getName(), newName);
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

                    // Create a new game instance on this server for this group of ready players
                    Game game = new Game(this);

                    // Only support single player games (for now)
                    game.addPlayerToGame(player.getKey(), player.getValue());
                    game.start();
                    games.add(game);

                    logger.info("Players in the lobby: {}", lobby.size());
                    logger.info("Running games: {}", games.size());
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
        } else {
            logger.warn("An error occurred on a connection. {}", (Object) ex.getStackTrace());
        }
    }

    @Override
    public void onStart() {
        logger.info("Server started successfully");
        regularlyCheckForEmptyGames();
    }

    void addPlayerToLobby(WebSocket webSocket, Player player) {
        lobby.put(webSocket, player);
        broadcastLobbyState();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    @Nullable GameOverStats getCurrentHighScore() {
        GameOverStats highScore;
        try (Session session = sessionFactory.openSession()) {
            NativeQuery<GameOverStats> query = session.createNativeQuery("SELECT * FROM `GameOverStats` " +
                            "WHERE DATE(finishedAt) BETWEEN CURRENT_DATE AND CURRENT_DATE " +
                            "ORDER BY projectsVolume DESC LIMIT 1",
                    GameOverStats.class);
            highScore = query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            logger.warn("Could not fetch high-score from database: {}", e.getMessage());
            return null;
        }
        return highScore;
    }

    private void regularlyCheckForEmptyGames() {
        ScheduledExecutorService regularTaskManager = Executors.newSingleThreadScheduledExecutor();
        regularTaskManager.scheduleAtFixedRate(() -> {
            if (!games.isEmpty()) {
                for (Game game : games) {
                    if (game.getPlayers().isEmpty()) {
                        logger.debug("Found ghost game! {}", game);
                        game.stop();
                        games.remove(game);
                        broadcastLobbyState();
                    }
                }
            }
        }, 0, 2500, TimeUnit.MILLISECONDS);
    }
}