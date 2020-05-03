package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 100;
    private static final int BANKRUPTCY_THRESHOLD = -50000;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GameServer gameServer;
    private final Map<WebSocket, Player> players;
    private final ArrayList<Project> projects;
    private int currentTick;
    private Date currentDate;
    private final ScheduledExecutorService executorService;
    private double projectSpawnProbability = 0.0;

    // Every GameServer hosts exactly one Game
    Game(Map<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        currentTick = 0;
        currentDate = new Date();
        projects = new ArrayList<>();
        logger.debug("A new game has started with {} players.", players.size());

        // Start running the game time
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            this.sendMessageToAllPlayers("{\"tick\": " + getCurrentTick() + "}");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 0, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private void progressGameTime() {
        ++currentTick;

        // Progress calendar date
        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);
        c.add(Calendar.DAY_OF_MONTH, 1);
        currentDate = c.getTime();

        // If it's the first day of the month, calculate salaries and decrease company funds accordingly
        if (isFirstDayOfMonth(c)) {
            logger.debug("Calculating funds for {} players...", players.size());
            players.forEach((webSocket, player) -> {
                player.calculateAndSubtractSalaries();

                // Send the new funds to the player
                JSONObject newStateEvent = new JSONObject();
                newStateEvent.put("eventType", "NEW_FUNDS");
                newStateEvent.put("funds", player.getFunds());
                sendMessageToPlayer(player, newStateEvent.toJSONString());
            });
        }
        
        // If a player is out of money, the game is over for that player. The other players can continue.
        players.forEach((webSocket, player) -> {
            if (player.getFunds() <= BANKRUPTCY_THRESHOLD) {
                JSONObject gameOverEvent = new JSONObject();
                gameOverEvent.put("eventType", "GAME_OVER");
                sendMessageToPlayer(player, gameOverEvent.toJSONString());

                // Tell Gameserver to move player back to lobby
                gameServer.addPlayer(webSocket, player);

                // Kick player out of the game
                removePlayer(webSocket);
            }
        });

        // Randomly spawn tenders for players to make some money
        if (new Random().nextFloat() >= 0.95) {
            // Generate a new project
            Project project = Project.generateRandomProject();
            projects.add(project);

            // Inform players of new project
            JSONObject newTenderEvent = new JSONObject();
            newTenderEvent.put("eventType", "NEW_TENDER");

            // Serialize a project as JSON string
            JSONObject newTenderJson = new JSONObject();
            newTenderJson.put("name", project.getName());
            newTenderJson.put("volume", project.getVolume());
            newTenderEvent.put("tender", newTenderJson);

            logger.debug("New tender '{}' spawned", project.getName());
            sendMessageToAllPlayers(newTenderEvent.toJSONString());
        }

        // TODO If any player participates in the tender, decide who'll get it

        // Decrease time left for tender
        for (Project project : projects) {
            project.decreaseTimeLeftForTender();

            if (project.getTimeLeftForTender() == 0) {
                // After 14 days, close the call for tender and award the winner
                JSONObject closeTenderEvent = new JSONObject();
                closeTenderEvent.put("eventType", "CLOSE_TENDER");
                closeTenderEvent.put("name", project.getName());
                sendMessageToAllPlayers(closeTenderEvent.toJSONString());
            }
        }
    }

    void sendMessageToAllPlayers(String message) {
        // Send the message to all players
        players.forEach((webSocket, player) -> webSocket.send(message));
    }

    void sendMessageToPlayer(Player player, String message) {
        logger.debug("Sending message to player {}: '{}'", player.getName(), message);

        // Get the WebSocket connection of the player
        WebSocket webSocket = getConnectionByPlayer(players, player);

        // Send a single message on that WebSocket connection
        webSocket.send(message);
    }

    private static WebSocket getConnectionByPlayer(Map<WebSocket, Player> map, Player value) {
        return map.keySet()
                    .stream()
                    .filter(key -> value.equals(map.get(key)))
                    .findFirst().get();
    }

    private boolean isFirstDayOfMonth(Calendar calendar) {
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar cannot be null.");
        }

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        return dayOfMonth == 1;
    }

    private int getCurrentTick() {
        return currentTick;
    }

    void removePlayer(WebSocket conn) {
        players.remove(conn);

        // Close game session if this was the last player
        if (players.size() == 0) {
            shutdown();
        }
    }

    Map<WebSocket, Player> getPlayers() {
        return players;
    }

    void shutdown() {
        gameServer = null;
        executorService.shutdownNow();
    }
}
