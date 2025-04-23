package de.andrenitze.softpro;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.GameState;
import de.andrenitze.softpro.domains.Savegame;
import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.LevelDecisions;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.projects.RiskLevel;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.GlobalGameEmptyEvent;
import de.andrenitze.softpro.events.GlobalGameOverEvent;
import de.andrenitze.softpro.repositories.SavegameRepository;
import de.andrenitze.softpro.services.PlayerService;
import de.andrenitze.softpro.services.impl.DecisionService;
import de.andrenitze.softpro.services.impl.GameLifeCycleService;
import de.andrenitze.softpro.services.impl.player.LobbyPlayerServiceImpl;
import de.andrenitze.softpro.types.GameOverStatsDAO;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.build.ToStringPlugin;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class GameServer extends WebSocketServer {
    private final GameFactory gameFactory;
    private final LobbyPlayerServiceImpl lobbyPlayerService;
    private final GameLifeCycleService gameLifeCycleService;
    private final SavegameRepository savegameRepository;
    private final GameOverStatsDAO gameOverStatsDAO;
    private final DecisionService decisionService;

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
    @Getter public static final Gson gson = new GsonBuilder()
            .addSerializationExclusionStrategy(strategy)
            .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
            .create();
    @Getter private GameOverStats dailyHighScore;
    public static final Random RANDOM = new SecureRandom();
    private List<GameOverStats> dailyHighScores;
    private List<GameOverStats> monthlyHighScores;
    private List<GameOverStats> quarterlyHighScores;

    /**
     * Creates a GameServer instance to manage games and players.
     */
    public GameServer(GameFactory gameFactory,
                      LobbyPlayerServiceImpl lobbyPlayerService,
                      GameLifeCycleService gameLifeCycleService,
                      SavegameRepository saveGameRepository) {
        super(new InetSocketAddress(DEFAULT_PORT));
        this.gameFactory = gameFactory;
        this.lobbyPlayerService = lobbyPlayerService;
        this.gameLifeCycleService = gameLifeCycleService;
        this.savegameRepository = saveGameRepository;
        this.gameOverStatsDAO = gameFactory.getGameOverStatsDAO();
        this.decisionService = gameFactory.getDecisionService();
    }

    @Override
    public void onStart() {
        init();
        log.info("Server started successfully");
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
            log.error("Could not get host address", e);
        }
        int port = getPort();
        String serverAddress = String.format("ws://%s:%d", hostAddress, port);
        log.info("Starting server at {}", serverAddress);
    }

    private void gracefulShutdown() {
        log.info("Shutting down server...");
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
        log.info("Server stopped.");
    }

    private void fetchHighScore() {
        log.info("Fetching high-score from database");

        List<GameOverStats> highScores = gameOverStatsDAO.getCurrentHighScores();

        if (highScores != null && !highScores.isEmpty()) {
            processHighScores(highScores);
        } else {
            log.info("No high-scores found in database");
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
                log.warn("Unknown period: {}", highScore.getPeriod());
        }
    }

    private void updateHighScores(List<GameOverStats> daily, List<GameOverStats> monthly, List<GameOverStats> quarterly) {
        if (!daily.isEmpty()) {
            this.dailyHighScores = daily;
            log.info("Daily high-scores fetched from database");
        } else {
            log.info("No daily high-scores set for today, yet.");
        }

        if (!monthly.isEmpty()) {
            this.monthlyHighScores = monthly;
            log.info("Monthly high-scores fetched from database");
        } else {
            log.info("No monthly high-scores set for this month, yet.");
        }

        if (!quarterly.isEmpty()) {
            this.quarterlyHighScores = quarterly;
            log.info("Quarterly high-scores fetched from database");
        } else {
            log.info("No quarterly high-scores set for this quarter, yet.");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
        // First, check if Spring Boot database connection is available. If not, close the WebSocket.
        if (gameOverStatsDAO == null) {
            log.error("Database connection is not available. Closing WebSocket.");
            webSocket.close();
            return;
        }

        // When a new WebSocket connection is opened, it's a player joining the lobby
        log.info("Client {} connected", webSocket.getRemoteSocketAddress());

        sendVersionAndGameSpeed(webSocket);

        Player newPlayer = new Player();

        // This needs to be replaced with the current level from the players' user account
        addPlayerToLobby(webSocket, newPlayer, 1); // First level
    }

    private void sendVersionAndGameSpeed(WebSocket webSocket) {
        final Properties properties = new Properties();
        try {
            properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
            String version = properties.getProperty("version");
            GameEvent<Map<String, Object>> versionEvent = new GameEvent<>(EventType.VERSION);
            Map<String, Object> payload = new HashMap<>();
            payload.put("version", version);
            payload.put("gameSpeed", gameLifeCycleService.getGameSpeedInMilliseconds());
            versionEvent.setPayload(payload);
            webSocket.send(GameServer.getGson().toJson(versionEvent));
        } catch (IOException e) {
            log.error("Could not load application.properties file");
        }
    }

    /**
     * Creates a new game instance for a player and adds her to it.
     * A new instance is created for each new level a player reaches.
     *
     * @param webSocket WebSocket   The WebSocket connection to the client
     * @param player Player         The player to be added to the game
     */
    private void createGame(WebSocket webSocket, Player player, int level) {
        log.debug("Creating new game instance for player {}", player.getId());
        AnnotationConfigApplicationContext gameContext = gameFactory.buildGameInstance();
        Game game = gameContext.getBean(Game.class);

        // Add the game context and game instance to the gameContexts map
        gameContexts.put(gameContext, game);
        log.debug("Added new context {} to now {} gameContexts.", gameContext.hashCode(), gameContexts.size());
        game.prepareLevelForPlayer(level);
        try {
            game.getPlayerService().addPlayer(webSocket, player); // Add player to game (player is now in game AND in lobby until the game starts)
        } catch (Exception e) {
            log.error("Could not add player to game: {}", e.getMessage());
            return;
        }
        log.debug("New game {} (level {}) created and prepared for player {}", this.hashCode(), game.getLevel(), player.getId());
        prepareForNextLevel(player, game);

        // Notify player about next level
        GameEvent<Integer> levelUpdateEvent = new GameEvent<>(EventType.LEVEL_UPDATED);
        levelUpdateEvent.setPayload(game.getLevel());
        webSocket.send(gson.toJson(levelUpdateEvent));
    }

    /**
     * Prepare the player and game instance for the next level while player is in "BRIEFING" state.
     * This can be in the lobby OR on the briefing screen.
     */
    private void prepareForNextLevel(Player player, Game game) {
        int level = game.getLevel();
        log.debug("Preparing game for level {} and player {}", level, player.getId());

        // Give player chance to prepare for the next level (read up, make decisions etc.)
        player.setReady(false);

        // Initialization methods change the player's state according to the player's level
        player.initializeObjectives(level);
        player.initializeFunds(level);
        player.setXp(0);

        // Make sure the skills are initialized
        game.getSkillService().initializePlayer(player);

        // For level 1, generate the player as his/her own first and only employee
        if (level == 1) {
            player.setEmployees(new ArrayList<>());
            Employee employee = new Employee(game.getTalentMarket().generateNewEmployeeId());
            employee.setFirstName(player.getFirstName());
            employee.setLastName(player.getLastName());
            employee.setSalary(458, 0);
            employee.setAge(22);
            employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.2f, "Highly motivated");

            // List all non-compliance project types
            List<ProjectType> nonComplianceTypes = Arrays.stream(ProjectType.values())
                    .filter(projectType -> projectType != ProjectType.COMPLIANCE)
                    .toList();

            // Increase XP in one random project domain and project type
            ProjectType randomType = nonComplianceTypes.get(RANDOM.nextInt(nonComplianceTypes.size()));
            String domain = randomType.getRandomDomain();
            employee.addXp(randomType, domain, 400);
            player.addEmployee(employee);

            // Generate a friendly low-risk project matching the player's skill
            Project perfectProject = new Project(randomType, domain, RiskLevel.LOW, false);
            game.getProjectService().addProject(perfectProject);

            // Generate two more random non-compliance projects
            for (int i = 0; i < 2; i++) {
                randomType = nonComplianceTypes.get(RANDOM.nextInt(nonComplianceTypes.size()));
                Project project = new Project(randomType,
                        randomType.getRandomDomain(),
                        RiskLevel.LOW,
                        false);
                game.getProjectService().addProject(project);
            }
        }

        if (level == 2) {
            // For level 2, populate the talent market with employees
            game.getTalentMarket().init();

            // ...and generate first employees for the player
            game.getPlayerService().generateFirstEmployeesForPlayers();
        }
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        log.debug("Connection closed: {} - Reason: {} - Remote: {}", webSocket.getRemoteSocketAddress(), reason, remote);
        removeDisconnectedClient(webSocket);
        broadcastLobbyState();
    }

    private void removeDisconnectedClient(WebSocket webSocket) {
        Player player = lobbyPlayerService.removePlayer(webSocket);
        if (player != null) {
            log.debug("Removed player {} from lobby", player.getId());

            // Go through game contexts, find the corresponding game and remove player references from the game
            for (Map.Entry<AnnotationConfigApplicationContext, Game> entry : gameContexts.entrySet()) {
                Game game = entry.getValue();
                if (game.getPlayerService().hasWebSocket(webSocket)) {
                    game.removePlayer(player);
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        log.debug("received message from {}: {}", webSocket.getRemoteSocketAddress(), message);

        try {
            GameEvent<?> genericGameEvent = gson.fromJson(message, GameEvent.class);

            if (EventType.PLAYER_READY.equals(genericGameEvent.getType())) {
                handlePlayerReadyEvent(webSocket, message);
            } else if (EventType.PLAYER_NAME_UPDATED.equals(genericGameEvent.getType())) {
                handlePlayerNameUpdatedEvent(webSocket, message);
            } else if (EventType.USER_LOGGED_IN.equals(genericGameEvent.getType())) {
                handleUserLogin(webSocket, message);
            } else {
                forwardEventToGame(webSocket, message);
            }
        } catch (JSONException | JsonSyntaxException e) {
            log.error("Received invalid websocket message: {}", e.getMessage());
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
        log.debug("GameServer/Lobby: Handling PLAYER_READY event for WebSocket {}", webSocket.getRemoteSocketAddress());
        try {
            Player player = lobbyPlayerService.getPlayer(webSocket);

            Type payloadType = new TypeToken<GameEvent<LevelDecisions>>() {}.getType();
            GameEvent<LevelDecisions> playerReadyEvent = GameServer.getGson().fromJson(message, payloadType);
            int level = playerReadyEvent.getPayload().level();
            List<Decision> decisions = playerReadyEvent.getPayload().decisions();
            player.setDecisionsForLevel(level, decisions);

            decisionService.saveDecisionsAsync(player.getId().toString(), gameLifeCycleService.getLevel(), player.getDecisionsByLevel(level));

            player.setReady(true);
            startReadyGames();
            broadcastLobbyState();
        } catch (Exception e) {
            log.error("Websocket message was malformed! {}", e.getMessage(), e);
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
            log.debug("{} changed name to {}", oldName, newName);
        }
    }

    private String sanitizePlayerName(String name) {
        return name.substring(0, Math.min(MAX_PLAYER_NAME_LENGTH, name.length()))
                .replaceAll("[^\\p{L}\\p{M}\\s]", "").trim();
    }

    private void handleUserLogin(WebSocket webSocket, String message) {
        // Parse "userId" and "sub" strings from the message
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> loginEvent = GameServer.getGson().fromJson(message, payloadType);
        UUID userId = UUID.fromString(loginEvent.getPayload().get("userId"));
        String sub = loginEvent.getPayload().get("sub");

        // Find player by websocket connection (and compare with userId)
        Player player = lobbyPlayerService.getPlayer(webSocket);
        if (player == null || sub == null || !player.getId().equals(userId)) {
            log.warn("Player not found for websocket connection or provided userId.");
            return;
        }

        // Update player with sub for identification after next login
        player.setJwtSubject(sub);

        // Reload game state if save game exists
        Optional<GameState> gameState = loadGame(player);
        if (gameState.isPresent()) {
            log.debug("Loading saved game state for player {}...", player.getId());
            GameState state = gameState.get();
            // Find game context for the player
            Game game = gameContexts.values().stream()
                    .filter(g -> g.getPlayerService().hasWebSocket(webSocket))
                    .findFirst()
                    .orElse(null);

            if (game != null) {
                game.restoreGameState(state, player);

                // Now notify the player about the restored game state
                GameEvent<Player> playerUpdatedEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
                playerUpdatedEvent.setPayload(player);
                webSocket.send(gson.toJson(playerUpdatedEvent));

                // Send level update event
                GameEvent<Integer> levelUpdateEvent = new GameEvent<>(EventType.LEVEL_UPDATED);
                levelUpdateEvent.setPayload(game.getLevel());
                webSocket.send(gson.toJson(levelUpdateEvent));

                // Update lobby
                broadcastLobbyState();
            } else {
                log.warn("Game instance not found for player {}. Game could not be loaded.", player.getId());
            }
        }
    }

    private void forwardEventToGame(WebSocket webSocket, String message) {
        boolean forwarded = false;
        for (Game game : gameContexts.values()) {
            if (game.getPlayerService().hasWebSocket(webSocket)) {
                log.debug("Forwarding message to game instance: {}", game.hashCode());
                game.getEventHandler().handleEvent(webSocket, message);
                forwarded = true;
                break;
            }
        }
        if (!forwarded) {
            log.warn("No game instance found for WebSocket: {}", webSocket.getRemoteSocketAddress());
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
                    log.error("Could not find game instance for player {}", player.getValue().getId());
                    return;
                }

                game.start();
            }
        }
    }

    public void broadcastLobbyState() {
        JSONArray playersList = new JSONArray();
        log.debug("Broadcasting lobby state to {} players in lobby.", lobbyPlayerService.getPlayers().size());
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

        GameEvent<Map<String, Object>> updateLobbyEvent = new GameEvent<>(EventType.UPDATE_LOBBY);
        Map<String, Object> payload = new HashMap<>();
        payload.put("runningGames", runningGames);
        payload.put("players", lobbyPlayerService.getPlayers().values());
        payload.put("dailyHighScores", anonymizedDailyHighScores);
        payload.put("monthlyHighScores", anonymizedMonthlyHighScores);
        payload.put("quarterlyHighScores", anonymizedQuarterlyHighScores);
        updateLobbyEvent.setPayload(payload);
        broadcast(gson.toJson(updateLobbyEvent));
        logLobbyState();
    }

    private void logLobbyState() {
        log.debug("Players in lobby/briefing: {} | Players in running games: {} | Active game contexts: {}",
                lobbyPlayerService.getPlayers().size(),
                gameContexts.values().stream().filter(Game::isRunning).count(),
                gameContexts.size());
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
        log.debug("received ByteBuffer from {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket webSocket, Exception ex) {
        // Most likely a player dropped out of the game and the WebSocket connection is gone
        if (webSocket != null) {
            log.warn("Connection {} was closed unexpectedly.", webSocket.getRemoteSocketAddress());
            // Kick player and close game if empty
            removeDisconnectedClient(webSocket);
        } else {
            log.warn("An error occurred on a connection. {}", (Object) ex.getStackTrace());
        }
    }

    public void addPlayerToLobby(WebSocket webSocket, Player player, int level) {
        lobbyPlayerService.addPlayer(webSocket, player);
        createGame(webSocket, player, level);

        // Send player state to the client
        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        webSocket.send(gson.toJson(playerUpdateEvent));

        broadcastLobbyState();
    }

    public void setNewHighScore(GameOverStats gameOverStats) {
        this.dailyHighScore = gameOverStats;
    }

    public List<GameOverStats> getCurrentHighScores() {
        List<GameOverStats> highScores = gameOverStatsDAO.getCurrentHighScores();
        if (highScores != null && !highScores.isEmpty()) {
            log.info("Current high score fetched successfully.");
        } else {
            log.warn("No high score found for today.");
        }
        return highScores;
    }

    public void saveGameOverStats(WebSocket webSocket, Player player, GameOverStats goStats) {
        // Complete the infos for the database
        goStats.setPlayerName(player.getName());
        goStats.setFinishedAt(new Date());
        goStats.setGameId(String.valueOf(this.hashCode()));
        goStats.setIpAddress(webSocket.getRemoteSocketAddress().toString());

        // Save high-score in a separate thread (optional: make async!)
        if (gameOverStatsDAO.saveGameOverStats(goStats)) {
            log.debug("Game stats of player in game {} saved successfully.", goStats.getGameId());
        } else {
            log.warn("Game stats of player in game {} could not be saved!", goStats.getGameId());
        }
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

    // Move a single player back to the lobby after game over
    public void movePlayerBackToLobby(Game game, Player player, WebSocket webSocket) {
        PlayerService gamePlayerService = game.getPlayerService();

        // Move player from game to lobby
        gamePlayerService.removePlayer(player);

        // Keep the level to create a correct new game instance (the old game context is already destroyed)
        addPlayerToLobby(webSocket, player, game.getLevel());
        player.setReady(false);

        broadcastLobbyState();
    }

    /**
     * Save the game state for an authenticated player.
     *
     * @param player The player whose game state should be saved (must be authenticated to use JWT's "sub" as ID.
     */
    public void saveGame(Game game, Player player) {
        String userId = player.getJwtSubject();
        GameState gameState = game.exportState(player);

        if (gameState == null) {
            log.warn("GameState is null – skipping save for player {}", userId);
            return;
        }

        String gameStateJson = gson.toJson(gameState);

        Savegame savegame = savegameRepository.findByUserId(userId)
                .orElseGet(Savegame::new);

        savegame.setUserId(userId);
        savegame.setLevel(game.getLevel());
        savegame.setGameStateJson(gameStateJson);
        savegame.setLastUpdated(Instant.now());

        savegameRepository.save(savegame);
        log.debug("Game state for player {} saved successfully.", userId);
    }


    private Optional<GameState> loadGame(Player player) {
        return savegameRepository.findByUserId(player.getJwtSubject())
                .map(savegame -> {
                    try {
                        return gson.fromJson(savegame.getGameStateJson(), GameState.class);
                    } catch (JsonSyntaxException e) {
                        log.warn("Failed to parse GameState for user {}: {}", player.getJwtSubject(), e.getMessage());
                        return null;
                    }
                });
    }

    @EventListener
    public void handleGameOverEvent(GlobalGameOverEvent event) {
        Game.GameOverData data = event.getGameOverData();
        log.debug("🚨 Global listener received GameOverEvent from game.");
        log.debug("Saving high-score and moving player {} back to lobby...", data.player().getId());

        // If player is logged in with a valid user account, save the game state
        if (!data.player().getJwtSubject().isEmpty()) {
            saveGame(data.game(), data.player());
        }

        movePlayerBackToLobby(data.game(), data.player(), data.webSocket());
        saveGameOverStats(data.webSocket(), data.player(), data.stats());
        checkAndBroadcastHighScore(data.stats());
    }

    @EventListener
    public void handleEmptyGameEvent(GlobalGameEmptyEvent event) {
        log.info("🚨 Global listener received GameEmptyEvent from game.");
        Game game = event.getGame();
        log.debug("Game {} is empty. Removing...", game.hashCode());
        removeGame(game);
        logLobbyState();
    }
}