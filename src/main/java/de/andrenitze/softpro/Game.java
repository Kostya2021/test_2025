package de.andrenitze.softpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Game {
    public static final int GAME_SPEED_IN_MILLISECONDS = 1000;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private int currentTick;
    private Date currentDate;

    // Every GameServer hosts exactly one Game
    public Game(GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        currentTick = 0;
        currentDate = new Date();
        logger.debug("A new game has started!");

        // Start running the game time
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(new Runnable() {
                @Override public void run() {
                    // Notify all clients of current time
                    gameServer.notifyAllClients("{\"tick\": "+ getCurrentTick()+"}");

                    // Do all kinds of calculations in the world
                    // ...

                    // Notify clients if there are any new events. These can be game-wide or player-specific.
                    //gameServer.broadcast("event...");

                    // Progress game time
                    progressGameTime();
                }
            }, 0, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private void progressGameTime() {
        ++currentTick;

        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);
        c.add(Calendar.DAY_OF_MONTH, 1);
        currentDate = c.getTime();
        logger.info(""+currentDate);
    }

    public int getCurrentTick() {
        return currentTick;
    }
}
