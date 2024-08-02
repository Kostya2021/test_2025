package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.entities.LevelDecisions;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.*;
import de.andrenitze.softpro.util.DatabaseConfig;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
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
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends WebSocketServer {
    public static final int MAX_PLAYER_NAME_LENGTH = 25;
    private final Set<Game> games = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WebSocket, Player> lobby = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Gson GSON = new Gson();
    private GameOverStats dailyHighScore;
    protected static final Random RANDOM = new Random();

    /**
     * Creates a GameServer instance to manage games and players
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));

        // Fetch high-score in a separate thread
        new Thread(this::fetchHighScore).start();

        // Graceful shutdown hook
        Thread printingHook = new Thread(this::gracefulShutdown);
        Runtime.getRuntime().addShutdownHook(printingHook);
    }

    private void gracefulShutdown() {
        logger.info("Shutting down server...");
        // Disconnect all client connections for lobby...
        for (WebSocket client : lobby.keySet()) {
            client.close();
        }

        // ...and for running games
        for (Game game : games) {
            for (WebSocket client : game.getPlayers().keySet()) {
                client.close();
            }
        }
        logger.info("Server stopped.");
    }

    private void fetchHighScore() {
        logger.info("Fetching high-score from database");
        GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(DatabaseConfig.getDataSource());

        GameOverStats dailyHighScore = gameOverStatsDAO.getCurrentHighScore();
        if (dailyHighScore != null && dailyHighScore.getProjectsVolume() > 0) {
            this.dailyHighScore = dailyHighScore;
            logger.info("Current high-score ({} € volume) fetched from database", this.dailyHighScore.getProjectsVolume());
        } else {
            logger.info("No high-score set for today, yet.");
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

        // Player is in the lobby and in a game at the same time because the game
        // needs to be initialized for the level briefing.
        // This is ok, because for multiplayer, players have to wait in the briefing room.
        lobby.put(webSocket, newPlayer);
        createNewGameWithPlayer(webSocket, newPlayer);

        broadcastLobbyState();

        logger.info("New player '{}' added. New number of players in lobby: {}",
                newPlayer.getName(),
                lobby.size());
    }

    /**
     * Creates a new game instance for a player and adds her to it.
     *
     * @param webSocket WebSocket   The WebSocket connection to the client
     * @param player Player         The player to be added to the game
     */
    private void createNewGameWithPlayer(WebSocket webSocket, Player player) {
        Game game = new Game(this);
        game.addPlayerToGame(webSocket, player);

        // Prepare both, player and game, for the next level
        preparePlayerAndGameForNextLevel(player, game);
        games.add(game);
        logger.debug("New game {} (level {}) created for player {}", this.hashCode(), player.getLevel(), player.getId());

        // Send updated player state to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        webSocket.send(GSON.toJson(playerUpdateEvent));
    }

    /**
     * Prepare the player and game instance for the next level while player is in "BRIEFING" state.
     * This can be in the lobby OR on the briefing screen.
     */
    private void preparePlayerAndGameForNextLevel(Player player, Game game) {
        logger.debug("Preparing game for level {} and player {}", player.getLevel(), player.getId());

        // Give player chance to prepare for the next level (read up, make decisions etc.)
        player.setReady(false);

        // Initialization methods change the player's state according to the player's level
        player.initializeObjectives();
        player.initializeFunds();

        // Load story elements for the next level
        // game.getLevel() is "1", because game has not been completely initialized
        // player.getLevel() is "2" already, because completed all objectives
        game.loadStory(player.getLevel());

        // Make sure the skills are initialized
        game.getSkillsManager().addPlayer(player);

        // For level 1, generate the player as his/her own first and only employee
        if (player.getLevel() == 1) {
            Employee employee = new Employee(game.getTalentMarket().generateNewEmployeeId());
            employee.setName(player.getName());
            employee.setSalary(952);
            employee.setAge(22);

            // Increase XP in one random project domain and project type
            ProjectType type = ProjectType.values()[RANDOM.nextInt(ProjectType.values().length)];
            String domain = type.getRandomDomain();
            employee.addXp(type, domain, 400);
            player.addEmployee(employee);

            // Generate a friendly low-risk project matching the player's skill
            Project perfectProject = new Project(type, domain, RiskLevel.low, false);
            game.addProject(perfectProject);

            // Generate two more random projects
            for (int i = 0; i < 2; i++) {
                game.addProject(new Project());
            }
        }

        if (player.getLevel() == 2) {
            // For level 2, populate the talent market with employees
            game.generateFirstEmployeesForPlayers();
        }
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        logger.debug("Connection {} closed", webSocket.getRemoteSocketAddress());
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

                if (game.closeGameIfEmpty()) {
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

                    List<Decision> decisions = gameEvent.getPayload().decisions();
                    int level = gameEvent.getPayload().level();
                    logger.debug("Received PLAYER_READY for level {}", level);

                    Player player = lobby.get(webSocket);

                    // Forward this event to the game event handler for level and player initialization tasks
                    for (Game game : games) {
                        if (game.hasWebSocket(webSocket)) {
                            game.getEventHandler().handleEvent(webSocket, message);
                        }
                    }

                    logger.debug("Player has level {}", player.getLevel());
                    logger.debug("Current game has level {}",
                            Objects.requireNonNull(games.stream()
                                            .filter(game -> game.hasWebSocket(webSocket))
                                            .findFirst()
                                            .orElse(null))
                                    .getLevel()
                    );
                    lobby.get(webSocket).setDecisions(level, decisions);
                    player.setReady(true);

                    broadcastLobbyState();

                    // Persist player decision(s) to database
                    DecisionDAO decisionDao = new DecisionDAO(DatabaseConfig.getDataSource());
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

                    // Change first employee name for level 1 accordingly
                    try {
                        player.getEmployees().get(0).setName(newName);
                    } catch (IndexOutOfBoundsException e) {
                        logger.error("No employees found for player {}", player.getName());
                    }

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

        // Anonymize high-score before sending
        GameOverStats anonymizedHighScore = getAnonymizedHighScore();

        // Calculate how many games are currently running (gameLoop.isRunning = true)
        short runningGames = (short) games.stream().filter(Game::isRunning).count();

        broadcast("{\"type\": \""+EventType.UPDATE_LOBBY+"\", \"payload\": { " +
                "\"runningGames\": " + runningGames +
                ", \"players\": " + playersList +
                ", \"highscore\": " + GSON.toJson(anonymizedHighScore) + "}}");
    }

    private GameOverStats getAnonymizedHighScore() {
        GameOverStats anonymizedHighScore = new GameOverStats();
        if (dailyHighScore != null && dailyHighScore.getDeliveredProjects() > 0) {
            anonymizedHighScore.setPlayerName(dailyHighScore.getPlayerName());
            anonymizedHighScore.setDeliveredProjects(dailyHighScore.getDeliveredProjects());
            anonymizedHighScore.setProjectsVolume(dailyHighScore.getProjectsVolume());
            anonymizedHighScore.setFinishedAt(dailyHighScore.getFinishedAt());
            anonymizedHighScore.setSurvivedDays(dailyHighScore.getSurvivedDays());
        }
        return anonymizedHighScore;
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

    void movePlayerToLobby(WebSocket webSocket, Player player) {
        logger.debug("Moving player {} back to lobby", player.getName());
        lobby.put(webSocket, player);

        // TODO Where does it belong?
        createNewGameWithPlayer(webSocket, player);

        broadcastLobbyState();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    GameOverStats getCurrentHighScore() {
        DataSource dataSource = DatabaseConfig.getDataSource();
        GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(dataSource);

        GameOverStats highScore = gameOverStatsDAO.getCurrentHighScore();
        if (highScore != null) {
            logger.info("Current high score fetched successfully.");
        } else {
            logger.warn("No high score found for today.");
        }
        return highScore;
    }

    public Set<Game> getGames() {
        return games;
    }
}