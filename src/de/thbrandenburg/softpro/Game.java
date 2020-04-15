package de.thbrandenburg.softpro;

import de.thbrandenburg.softpro.Server.GameServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Game {
    private GameServer gameServer;
    private int tick = 0;

    // TODO It should be possible to host several Games[] on a single GameServer
    public Game(GameServer gameServer) {
        this.gameServer = gameServer;
        // Every game consists of players and a world in a specific state
        tick = 0;
        System.out.println("A new game has been created! Time is " + getTick() + ".");

        // Let the world's time start running
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(new Runnable() {
                @Override public void run() {
                    // Notify all clients of current time
                    gameServer.notifyAllClients("{\"tick\": "+getTick()+"}");

                    // Do all kinds of calculations in the world
                    // ...

                    // Notify clients if there are any new events. These can be game-wide or player-specific.
                    //gameServer.broadcast("event...");

                    // Progress server game time
                    ++tick;
                    System.out.println(tick);
                }
            }, 0, 1, TimeUnit.SECONDS);
    }

    public int getTick() {
        return tick;
    }
}
