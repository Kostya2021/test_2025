package de.thbrandenburg.sim;

import com.esotericsoftware.kryonet.Server;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

public class Game {
    public void start() {
        Server server = new Server();
        server.start();

        try {
            server.bind(54555, 54777);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
