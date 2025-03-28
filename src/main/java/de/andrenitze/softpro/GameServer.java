package de.andrenitze.softpro;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.config.DatabaseConfig;
import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.LevelDecisions;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.projects.RiskLevel;
import de.andrenitze.softpro.events.*;
import de.andrenitze.softpro.services.DecisionService;
import de.andrenitze.softpro.services.GameLifeCycleService;
import de.andrenitze.softpro.services.LobbyPlayerService;
import de.andrenitze.softpro.services.impl.ObjectiveServiceImpl;
import de.andrenitze.softpro.types.GameOverStatsDAO;
import lombok.Getter;
import lombok.Setter;
import net.bytebuddy.build.ToStringPlugin;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.Game.GAME_SPEED_IN_MILLISECONDS;

@Component
public class GameServer extends WebSocketServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final GameFactory gameFactory;
    private final LobbyPlayerService lobbyPlayerService;

    @Getter @Setter private Map<AnnotationConfigApplicationContext, Game> gameContexts = new ConcurrentHashMap<>();

    public static final int DEFAULT_PORT = 80;
    public static final int MAX_PLAYER_NAME_LENGTH = 25;
    private static final ExclusionStrategy strategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            return field.getAnnotation(ToStringPlugin.Exclude.class) != null;
        }
    };
    @Getter public static final Gson gson = new GsonBuilder().addSerializationExclusionStrategy(strategy).create();
    @Getter private GameOverStats dailyHighScore;
    public static final Random RANDOM = new Random();
    private List<GameOverStats> dailyHighScores;
    private List<GameOverStats> monthlyHighScores;
    private List<GameOverStats> quarterlyHighScores;

    /**
     * Creates a GameServer instance to manage games and players.
     */
    @Autowired
    public GameServer(GameFactory gameFactory,
                      LobbyPlayerService lobbyPlayerService) {
        super(new InetSocketAddress(DEFAULT_PORT));
        this.gameFactory = gameFactory;
        this.lobbyPlayerService = lobbyPlayerService;
    }

    @Override
    public void onStart() {
        init();
        logger.info("Server started successfully");
    }

    public void init() {
        // Fetch high-score in a separate thread
        new Thread(this::fetchHighScore).start();

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::gracefulShutdown));

        String hostAddress = "";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.error("Could not get host address", e);
        }
        int port = getPort();
        String serverAddress = String.format("ws://%s:%d", hostAddress, port);
        logger.info("Starting server at {}", serverAddress);
    }

    private void gracefulShutdown() {
        logger.info("Shutting down server...");
        // Disconnect all client connections for lobby...
        for (WebSocket client : lobbyPlayerService.getPlayers().keySet()) {
            client.close();
        }

        // ...and for running games
        for (Game game : gameContexts.values()) {
            for (WebSocket client : game.getPlayerService().getPlayers().keySet()) {
                client.close();
            }
        }
        logger.info("Server stopped.");
    }

    private void fetchHighScore() {
        logger.info("Fetching high-score from database");
        GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(DatabaseConfig.getDataSource());

        List<GameOverStats> highScores = gameOverStatsDAO.getCurrentHighScores();

        if (highScores != null && !highScores.isEmpty()) {
            processHighScores(highScores);
        } else {
            logger.info("No high-scores found in database");
        }
    }

    private void processHighScores(List<GameOverStats> highScores) {
        List<GameOverStats> newDailyHighScores = new ArrayList<>();
        List<GameOverStats> newMonthlyHighScores = new ArrayList<>();
        List<GameOverStats> newQuarterlyHighScores = new ArrayList<>();

        for (GameOverStats highScore : highScores) {
            addHighScoreToList(highScore, newDailyHighScores, newMonthlyHighScores, newQuarterlyHighScores);
        }

        updateHighScores(newDailyHighScores, newMonthlyHighScores, newQuarterlyHighScores);
    }

    private void addHighScoreToList(GameOverStats highScore, List<GameOverStats> daily, List<GameOverStats> monthly, List<GameOverStats> quarterly) {
        switch (highScore.getPeriod()) {
            case "daily":
                daily.add(highScore);
                break;
            case "monthly":
                monthly.add(highScore);
                break;
            case "quarterly":
                quarterly.add(highScore);
                break;
            default:
                logger.warn("Unknown period: {}", highScore.getPeriod());
        }
    }

    private void updateHighScores(List<GameOverStats> daily, List<GameOverStats> monthly, List<GameOverStats> quarterly) {
        if (!daily.isEmpty()) {
            this.dailyHighScores = daily;
            logger.info("Daily high-scores fetched from database");
        } else {
            logger.info("No daily high-scores set for today, yet.");
        }

        if (!monthly.isEmpty()) {
            this.monthlyHighScores = monthly;
            logger.info("Monthly high-scores fetched from database");
        } else {
            logger.info("No monthly high-scores set for this month, yet.");
        }

        if (!quarterly.isEmpty()) {
            this.quarterlyHighScores = quarterly;
            logger.info("Quarterly high-scores fetched from database");
        } else {
            logger.info("No quarterly high-scores set for this quarter, yet.");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
        // First, check if a database connection is available. If not, don't allow any connections.
        try {
            DatabaseConfig.getDataSource().getConnection().close();
        } catch (Exception e) {
            logger.error("Database connection not available. Refusing websocket connection.");
            webSocket.close();
            return;
        }

        // When a new WebSocket connection is opened, it's a player joining the lobby
        logger.info("Client {} connected", webSocket.getRemoteSocketAddress());

        sendVersionAndGameSpeed(webSocket);

        Player newPlayer = new Player();
        addPlayerToLobby(webSocket, newPlayer);
    }

    private void sendVersionAndGameSpeed(WebSocket webSocket) {
        final Properties properties = new Properties();
        try {
            properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
            String version = properties.getProperty("version");
            GameEvent<Map<String, Object>> versionEvent = new GameEvent<>(EventType.VERSION);
            Map<String, Object> payload = new HashMap<>();
            payload.put("version", version);
            payload.put("gameSpeed", GAME_SPEED_IN_MILLISECONDS);
            versionEvent.setPayload(payload);
            webSocket.send(GameServer.getGson().toJson(versionEvent));
        } catch (IOException e) {
            logger.error("Could not load application.properties file");
        }
    }

    /**
     * Creates a new game instance for a player and adds her to it.
     * A new instance is created for each new level a player reaches.
     *
     * @param webSocket WebSocket   The WebSocket connection to the client
     * @param player Player         The player to be added to the game
     */
    private void createGame(WebSocket webSocket, Player player) {
        logger.debug("Creating new game instance for player {}", player.getId());
        AnnotationConfigApplicationContext gameContext = gameFactory.buildGameInstance();
        Game game = gameContext.getBean(Game.class);
        game.setEventHandler(new GameEventHandler(
                game.getMessagingService(),
                game.getPlayerService(),
                game.getEmployeeService(),
                game.getTalentMarket(),
                game.getProjectService(),
                game.getLifeCycleService(),
                game.getSkillService(),
                game.getProjectEmployeeService()));
        game.setObjectiveService(new ObjectiveServiceImpl(game.getPlayerService(),
                game.getLifeCycleService(),
                game.getProjectService(),
                game.getSkillService(),
                game.getProjectEmployeeService()));
        game.setLifeCycleService(gameContext.getBean(GameLifeCycleService.class));

        // Add the game context and game instance to the gameContexts map
        gameContexts.put(gameContext, game);
        logger.debug("Added new game context {} to {} existing gameContexts.", gameContext.hashCode(), gameContexts.size());
        game.addPlayerToLobby(webSocket, player); // -> Player is now in game and in lobby at the same time
        logger.debug("New game {} (level {}) created for player {}", this.hashCode(), player.getLevel(), player.getId());
        prepareForNextLevel(player, game);

        // Send updated player state to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        webSocket.send(gson.toJson(playerUpdateEvent));
    }

    /**
     * Prepare the player and game instance for the next level while player is in "BRIEFING" state.
     * This can be in the lobby OR on the briefing screen.
     */
    private void prepareForNextLevel(Player player, Game game) {
        logger.debug("Preparing game for level {} and player {}", player.getLevel(), player.getId());

        // Give player chance to prepare for the next level (read up, make decisions etc.)
        player.setReady(false);

        // Initialization methods change the player's state according to the player's level
        player.initializeObjectives();
        player.initializeFunds();
        player.setXp(0);

        // Make sure the skills are initialized
        game.getSkillService().addPlayer(player);

        // Load problems for the next level
        game.getProjectService().loadProblems(game.getLevel());

        // Load story elements for the next level
        // game.getLevel() is "1", because game has not been completely initialized
        // player.getLevel() is "2" already, because player has completed all objectives
        game.loadStory(player.getLevel());

        logger.debug("player level is {}, game level is {}", player.getLevel(), game.getLevel());

        // For level 1, generate the player as his/her own first and only employee
        if (player.getLevel() == 1) {
            player.setEmployees(new ArrayList<>());
            Employee employee = new Employee(game.getTalentMarket().generateNewEmployeeId());
            employee.setFirstName(player.getFirstName());
            employee.setLastName(player.getLastName());
            employee.setSalary(458, 0);
            employee.setAge(22);
            employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.2f, "Highly motivated");

            // Increase XP in one random project domain and project type (exclude "COMPLIANCE"!)
            ProjectType type;
            do {
                type = ProjectType.values()[RANDOM.nextInt(ProjectType.values().length)];
            } while (type == ProjectType.COMPLIANCE);
            String domain = type.getRandomDomain();

            employee.addXp(type, domain, 400);
            player.addEmployee(employee, 0);

            // Generate a friendly low-risk project matching the player's skill
            Project perfectProject = new Project(type, domain, RiskLevel.LOW, false);
            game.getProjectService().addProject(perfectProject);

            // Generate two more random non-compliance projects
            for (int i = 0; i < 2; i++) {
                Project project = new Project(ProjectType.values()[RANDOM.nextInt(ProjectType.values().length)],
                        type.getRandomDomain(),
                        RiskLevel.LOW,
                        false);
                game.getProjectService().addProject(project);
            }
        }

        if (player.getLevel() == 2) {
            // For level 2, populate the talent market with employees
            game.getTalentMarket().initialize();

            // ...and generate first employees for the player
            game.getPlayerService().generateFirstEmployeesForPlayers();
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
        Player player = lobbyPlayerService.removePlayer(webSocket);
        if (player != null) {
            logger.info("Player '{}' disconnected. New number of players in lobby: {}",
                    player.getId(),
                    lobbyPlayerService.getPlayers().size());
        }

        // Remove disconnected client from running game
        for (Game game : gameContexts.values()) {
            logger.debug("Checking game {} for disconnected client", game.hashCode());
            if (game.getPlayerService().hasWebSocket(webSocket)) {
                logger.debug("Removing player from game {}", game.hashCode());
                game.removePlayer(webSocket);

                // Don't search any further
                break;
            }
        }
        broadcastLobbyState();
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        logger.debug("received message from {}: {}", webSocket.getRemoteSocketAddress(), message);

        try {
            GameEvent<?> genericGameEvent = gson.fromJson(message, GameEvent.class);

            if (EventType.PLAYER_READY.equals(genericGameEvent.getType())) {
                handlePlayerReadyEvent(webSocket, message);
            } else if (EventType.PLAYER_NAME_UPDATED.equals(genericGameEvent.getType())) {
                handlePlayerNameUpdatedEvent(webSocket, message);
            } else {
                forwardEventToGame(webSocket, message);
            }
        } catch (JSONException | JsonSyntaxException e) {
            logger.error("Received invalid websocket message: {}", e.getMessage());
        }
    }

    /**
     * Prepare next level, save decisions, set player to ready and start the game loop,
     * if all players in the session are ready.
     *
     * @param webSocket  The WebSocket connection to the client
     * @param message    The message received from the client
     */
    private void handlePlayerReadyEvent(WebSocket webSocket, String message) {
        logger.debug("GameServer/Lobby: Handling PLAYER_READY event for WebSocket {}", webSocket.getRemoteSocketAddress());
        try {
            Player player = lobbyPlayerService.getPlayer(webSocket);
            DecisionService decisionService = new DecisionService();

            Type payloadType = new TypeToken<GameEvent<LevelDecisions>>() {}.getType();
            GameEvent<LevelDecisions> playerReadyEvent = GameServer.getGson().fromJson(message, payloadType);
            int level = playerReadyEvent.getPayload().level();
            List<Decision> decisions = playerReadyEvent.getPayload().decisions();
            player.setDecisions(level, decisions);
            decisionService.saveDecisionsAsync(player.getId().toString(), player.getLevel(), player.getDecisionsByLevel(level));

            for (Game game : gameContexts.values()) {
                if (game.getPlayerService().hasWebSocket(webSocket)) {
                    game.prepareNextLevel();
                }
            }

            player.setReady(true);
            logLobbyState();
            startReadyGames();
            logLobbyState();
            broadcastLobbyState();
        } catch (Exception e) {
            logger.debug(e.getMessage());
            logger.error("Websocket message was malformed!");
        }
    }

    private void handlePlayerNameUpdatedEvent(WebSocket webSocket, String message) {
        Type payloadType = new TypeToken<GameEvent<Player>>() {}.getType();
        GameEvent<Player> updatedPlayerEvent = gson.fromJson(message, payloadType);
        Player updatedPlayer = updatedPlayerEvent.getPayload();

        String newName = sanitizePlayerName(updatedPlayer.getName());
        if (newName.length() >= 2) {
            Player player = lobbyPlayerService.getPlayer(webSocket);
            String oldName = player.getName();
            player.setName(newName);

            GameEvent<Player> playerUpdateEvent = new GameEvent<>();
            playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
            playerUpdateEvent.setPayload(player);
            webSocket.send(gson.toJson(playerUpdateEvent));

            broadcastLobbyState();
            logger.info("{} changed name to {}", oldName, newName);
        }
    }

    private String sanitizePlayerName(String name) {
        return name.substring(0, Math.min(MAX_PLAYER_NAME_LENGTH, name.length()))
                .replaceAll("[^\\p{L}\\p{M}\\s]", "").trim();
    }

    private void forwardEventToGame(WebSocket webSocket, String message) {
        boolean forwarded = false;
        for (Game game : gameContexts.values()) {
            if (game.getPlayerService().hasWebSocket(webSocket)) {
                logger.debug("Forwarding message to game instance: {}", game.hashCode());
                game.getEventHandler().handleEvent(webSocket, message);
                forwarded = true;
                break;
            }
        }
        if (!forwarded) {
            logger.warn("No game instance found for WebSocket: {}", webSocket.getRemoteSocketAddress());
        }
    }

    /**
     * Start all games that have the required amount of players who are ready.
     * Also, move players out of the lobby.
     */
    private void startReadyGames() {
        for (Map.Entry<WebSocket, Player> player : lobbyPlayerService.getPlayers().entrySet()) {
            if (player.getValue().isReady()) {
                lobbyPlayerService.removePlayer(player.getKey()); // On game start, player is removed from lobby

                Game game = null;
                for (Game g : gameContexts.values()) {
                    if (g.getPlayerService().hasWebSocket(player.getKey())) {
                        game = g;
                        break;
                    }
                }

                if (game == null) {
                    logger.error("Could not find game instance for player {}", player.getValue().getId());
                    return;
                }

                game.start();
            }
        }
    }

    public void broadcastLobbyState() {
        JSONArray playersList = new JSONArray();
        logger.debug("Broadcasting lobby state to {} players in lobby.", lobbyPlayerService.getPlayers().size());
        for (Map.Entry<WebSocket, Player> entry : lobbyPlayerService.getPlayers().entrySet()) {
            Player readyPlayer = entry.getValue();
            JSONObject player = new JSONObject();
            player.put("id", readyPlayer.getId().toString());
            player.put("name", readyPlayer.getName());
            player.put("isReady", readyPlayer.isReady());
            playersList.put(player);
        }

        // Anonymize high-scores before sending
        List<GameOverStats> anonymizedDailyHighScores = getAnonymizedDailyHighScores();
        List<GameOverStats> anonymizedMonthlyHighScores = getAnonymizedMonthlyHighScores();
        List<GameOverStats> anonymizedQuarterlyHighScores = getAnonymizedQuarterlyHighScores();

        // Calculate how many games are currently running (gameLoop.isRunning = true)
        short runningGames = (short) gameContexts.values().stream().filter(Game::isRunning).count();

        broadcast("{\"type\": \""+EventType.UPDATE_LOBBY+"\", \"payload\": { " +
                "\"runningGames\": " + runningGames +
                ", \"players\": " + playersList +
                ", \"dailyHighScores\": " + gson.toJson(anonymizedDailyHighScores) +
                ", \"monthlyHighScores\": " + gson.toJson(anonymizedMonthlyHighScores) +
                ", \"quarterlyHighScores\": " + gson.toJson(anonymizedQuarterlyHighScores) + "}}");
        logLobbyState();
    }

    private void logLobbyState() {
        logger.debug("Players in lobby/briefing: {} | Players in running games: {}",
                lobbyPlayerService.getPlayers().size(), gameContexts.values().stream().filter(Game::isRunning).count());
    }

    private List<GameOverStats> getAnonymizedHighScores(List<GameOverStats> highScores) {
        return highScores.stream().map(highScore -> {
            GameOverStats anonymizedHighScore = new GameOverStats();
            anonymizedHighScore.setPlayerName(highScore.getPlayerName());
            anonymizedHighScore.setDeliveredProjects(highScore.getDeliveredProjects());
            anonymizedHighScore.setProjectsVolume(highScore.getProjectsVolume());
            anonymizedHighScore.setFinishedAt(highScore.getFinishedAt());
            anonymizedHighScore.setSurvivedDays(highScore.getSurvivedDays());
            return anonymizedHighScore;
        }).toList();
    }

    private List<GameOverStats> getAnonymizedDailyHighScores() {
        if (dailyHighScores != null && !dailyHighScores.isEmpty()) {
            return getAnonymizedHighScores(dailyHighScores);
        } else {
            return new ArrayList<>();
        }
    }

    private List<GameOverStats> getAnonymizedMonthlyHighScores() {
        if (monthlyHighScores != null && !monthlyHighScores.isEmpty()) {
            return getAnonymizedHighScores(monthlyHighScores);
        } else {
            return new ArrayList<>();
        }
    }

    private List<GameOverStats> getAnonymizedQuarterlyHighScores() {
        if (quarterlyHighScores != null && !quarterlyHighScores.isEmpty()) {
            return getAnonymizedHighScores(quarterlyHighScores);
        } else {
            return new ArrayList<>();
        }
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

    public void addPlayerToLobby(WebSocket webSocket, Player player) {
        logLobbyState();
        logger.debug("Adding player {} to lobby", player.getId());
        lobbyPlayerService.addPlayer(webSocket, player);
        logLobbyState();
        createGame(webSocket, player);
        broadcastLobbyState();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    public List<GameOverStats> getCurrentHighScores() {
        DataSource dataSource = DatabaseConfig.getDataSource();
        GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(dataSource);

        List<GameOverStats> highScores = gameOverStatsDAO.getCurrentHighScores();
        if (highScores != null) {
            logger.info("Current high score fetched successfully.");
        } else {
            logger.warn("No high score found for today.");
        }
        return highScores;
    }

    public void removeGame(Game game) {
        AnnotationConfigApplicationContext context = null;
        for (Map.Entry<AnnotationConfigApplicationContext, Game> entry : gameContexts.entrySet()) {
            if (entry.getValue().equals(game)) {
                context = entry.getKey();
                break;
            }
        }
        if (context != null) {
            context.close(); // Properly destruct the game context with all its beans
            gameContexts.remove(context);
        }
    }

    public void saveGameOverStats(WebSocket webSocket, Player player, GameOverStats goStats) {
        DataSource dataSource = DatabaseConfig.getDataSource();
        GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(dataSource);

        // Complete the infos for the database
        goStats.setPlayerName(player.getName());
        goStats.setFinishedAt(new Date());
        goStats.setGameId(String.valueOf(this.hashCode()));
        goStats.setIpAddress(webSocket.getRemoteSocketAddress().toString());

        // Save high-score in a separate thread
        if (gameOverStatsDAO.saveGameOverStats(goStats)) {
            logger.debug("Game stats of player in game {} saved successfully.", goStats.getGameId());
        } else {
            logger.warn("Game stats of player in game {} could not be saved!", goStats.getGameId());
        }
    }

    /**
     * Check if there's a new high-score and broadcast updates in lobby.
     */
    public void checkAndBroadcastHighScore(GameOverStats goStats) {
        if (isNewHighScore(goStats)) {
            setNewHighScore(goStats);
            broadcastLobbyState();
        }
    }

    public boolean isNewHighScore(GameOverStats highScoreCandidate) {
        List<GameOverStats> highScores = getCurrentHighScores();
        if (highScores == null) return false;

        return highScores.stream().anyMatch(highScore ->
                highScore.getProjectsVolume() < highScoreCandidate.getProjectsVolume());
    }

    @EventListener
    public void handleGameOverEvent(GlobalGameOverEvent event) {
        Game.GameOverData data = event.getGameOverData();
        logger.debug("🚨 Global listener received GameOverEvent from game.");
        logger.debug("Saving high-score and moving player {} back to lobby...", data.player().getId());
        try {
        movePlayerBackToLobby(data.game(), data.player(), data.webSocket());
        } catch (Exception e) {
            logger.error("Error while handling game over event: {}", e.getMessage());
        }
        saveGameOverStats(data.webSocket(), data.player(), data.stats());
        checkAndBroadcastHighScore(data.stats());

    }

    // Move a single player back to the lobby after game over
    public void movePlayerBackToLobby(Game game, Player player, WebSocket webSocket) {
        LobbyPlayerService gamePlayerService = game.getPlayerService();

        // Move player from game to lobby
        gamePlayerService.removePlayer(player);
        lobbyPlayerService.addPlayer(webSocket, player);

        broadcastLobbyState();
    }

    @EventListener
    public void handleEmptyGameEvent(GlobalGameEmptyEvent event) {
        logger.info("🚨 Global listener received GameEmptyEvent from game.");
        Game game = event.getGame();
        logger.debug("Running games: {} | Game contexts: {}", gameContexts.values().stream().filter(Game::isRunning).count(), gameContexts.size());
        logger.debug("Game {} is empty. Removing...", game.hashCode());
        removeGame(game);
        logger.debug("Running games: {} | Game contexts: {}", gameContexts.values().stream().filter(Game::isRunning).count(), gameContexts.size());
    }
}