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
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 2;
    public static final int MAX_PLAYER_NAME_LENGTH = 25;
    private final HashSet<Game> games = new HashSet<>();
    private final ConcurrentHashMap<WebSocket, Player> playersAndTheirConnections = new ConcurrentHashMap<>();
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

        // Initialize database session
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure("hibernate.cfg.xml")
                .build();

        try {
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
            this.dailyHighScore = getCurrentHighScore();
            if (this.dailyHighScore != null) {
                logger.info("Current high-score fetched from database");
            }
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
        // When a new WebSocket connection is opened, it's a player joining the lobby
        logger.info("Client {} connected", webSocket.getRemoteSocketAddress());
        Player generatedPlayer = new Player();
        playersAndTheirConnections.put(webSocket, generatedPlayer);

        // Return generated player to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(generatedPlayer);
        webSocket.send(GSON.toJson(playerUpdateEvent));

        broadcastPlayerList();

        logger.info("New player '{}' added. New number of players in lobby: {}",
                generatedPlayer.getName(),
                playersAndTheirConnections.size());
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        // Remove disconnected clients from lobby
        playersAndTheirConnections.remove(webSocket);

        // Remove disconnected clients from all running games
        for (Game game : games) {
            game.removePlayerFromGame(webSocket);
            logger.debug("A player left the game ({} players are left in the game)", game.getPlayers().size());

            if (game.closeIfEmpty()) {
                games.remove(game);
                logger.info("Running games: {}", games.size());
            }
        }
        broadcastPlayerList();
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

                    Player player = playersAndTheirConnections.get(webSocket);
                    player.setReady(true);
                    broadcastPlayerList();
                } catch (Exception e) {
                    logger.error("Websocket message was malformed!");
                }
            } else if (genericGameEvent.isOfType(EventType.PLAYER_NAME_UPDATED)) {
                // Allow name changes in the lobby
                Type payloadType = new TypeToken<GameEvent<Player>>() {}.getType();
                GameEvent<Player> updatedPlayerEvent = GSON.fromJson(message, payloadType);
                Player updatedPlayer = updatedPlayerEvent.getPayload();

                // Sanitize string
                String newName = updatedPlayer.getName();
                newName = newName.substring(0, Math.min(MAX_PLAYER_NAME_LENGTH, newName.length())).replaceAll("[^A-Za-z0-9 ]","").trim();
                if (newName.length() >= 2) {
                    Player player = this.playersAndTheirConnections.get(webSocket);
                    player.setName(newName);

                    // Confirm successful name change
                    GameEvent<Player> playerUpdateEvent = new GameEvent<>();
                    playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
                    playerUpdateEvent.setPayload(player);
                    webSocket.send(GSON.toJson(playerUpdateEvent));

                    // Notify everyone in the lobby
                    broadcastPlayerList();
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

            // Start a new game round if enough players in the lobby are ready
            int readyPlayersInLobby = 0;
            for (Player player : playersAndTheirConnections.values()) {
                if (player.isReady()) {
                    readyPlayersInLobby++;
                }
            }

            // Start games for all ready player groups in the lobby
            if (readyPlayersInLobby >= PLAYERS_NEEDED_FOR_GAME_START) {
                ConcurrentHashMap<WebSocket, Player> readyPlayersAndTheirConnections = new ConcurrentHashMap<>();
                for (Map.Entry<WebSocket, Player> player : playersAndTheirConnections.entrySet()) {
                    if (player.getValue().isReady()) {
                        // Move player from lobby to game
                        readyPlayersAndTheirConnections.put(player.getKey(), playersAndTheirConnections.remove(player.getKey()));
                    }

                    if (readyPlayersAndTheirConnections.size() == PLAYERS_NEEDED_FOR_GAME_START) {
                        // Create a new game instance on this server for this group of ready players
                        games.add(new Game(readyPlayersAndTheirConnections, this));
                        logger.info("Running games: {}", games.size());
                        logger.info("Players in the lobby: {}", playersAndTheirConnections.size());
                        broadcastPlayerList();
                    }
                }
            }
        } catch (JSONException | JsonSyntaxException e) {
            logger.error("Received invalid websocket message: {}", e.getMessage());
        }
    }

    private void broadcastPlayerList() {
        JSONArray playersList = new JSONArray();
        for (Map.Entry<WebSocket, Player> entry : playersAndTheirConnections.entrySet()) {
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

        broadcast("{\"type\": \""+EventType.UPDATE_LOBBY+"\", \"payload\": { \"players\": " + playersList +
                ", \"highscore\": " + GSON.toJson(anonymizedHighScore) + "}}");
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteBuffer message) {
        logger.debug("received ByteBuffer from {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket webSocket, Exception ex) {
        // Most likely a player dropped out of the game and the WebSocket is null
        if (webSocket != null) {
            logger.warn("An error occurred on connection {} : {}", webSocket.getRemoteSocketAddress(), ex.getStackTrace());
        } else {
            logger.warn("An error occurred on a connection. {}", (Object) ex.getStackTrace());
        }
    }

    @Override
    public void onStart() {
        logger.info("Server started successfully");
    }

    void addPlayerToLobby(WebSocket webSocket, Player player) {
        playersAndTheirConnections.put(webSocket, player);
        broadcastPlayerList();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    @Nullable GameOverStats getCurrentHighScore() {
        GameOverStats highScore;
        try (Session session = sessionFactory.openSession()) {
            NativeQuery<GameOverStats> query = session.createNativeQuery("SELECT * FROM `GameOverStats` " +
                            "WHERE DATE(finishedAt) = CURDATE() " +
                            "ORDER BY projectsVolume DESC LIMIT 1",
                    GameOverStats.class);
            highScore = query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return highScore;
    }
}