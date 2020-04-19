package de.andrenitze.softpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 1000;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private HashMap<String, Player> players;
    private int currentTick;
    private Date currentDate;

    // Every GameServer hosts exactly one Game
    Game(HashMap<String, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        currentTick = 0;
        currentDate = new Date();
        logger.debug("A new game has started!");

        // Start running the game time
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            gameServer.notifyAllClients("{\"tick\": "+ getCurrentTick()+"}");

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
            System.out.println("Erster des Monats. Gehälter abziehen.");

            for (Map.Entry<String, Player> entry : this.players.entrySet()) {
                Player player = entry.getValue();
                player.calculateAndSubtractSalaries();
            }
        }
    }

    private boolean isFirstDayOfMonth(Calendar calendar){
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar cannot be null.");
        }

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        return dayOfMonth == 1;
    }

    private int getCurrentTick() {
        return currentTick;
    }
}
