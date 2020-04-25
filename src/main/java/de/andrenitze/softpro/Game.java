package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 1000;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GameServer gameServer;
    private Map<WebSocket, Player> players;
    private int currentTick;
    private Date currentDate;
    private ScheduledExecutorService executorService;

    // Every GameServer hosts exactly one Game
    Game(Map<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        currentTick = 0;
        currentDate = new Date();
        logger.debug("A new game has started with {} players.", players.size());

        // Start running the game time
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            this.gameServer.notifyAllClients("{\"tick\": " + getCurrentTick() + "}");

            // Do all kinds of calculations in the world
            // ...

            // Notify clients if there are any new events. These can be game-wide or player-specific.
            //gameServer.broadcast("event...");

            // Progress game time
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
    }

    void sendMessageToPlayer(Player player, String message) {
        logger.debug("Sending message to player {}: '{}'", player.getName(), message);

        // Get the WebSocket connection of the player
        WebSocket conn = getConnectionByPlayer(players, player);

        // Send a single message on that WebSocket connection
        conn.send(message);
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
    }

    Map<WebSocket, Player> getPlayers() {
        return players;
    }
}
