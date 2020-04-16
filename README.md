# Software Project Simulator
This is the central game server that can be deployed to and run in an infrastructure as a standard Java 11 application.

## Run the game server
To run the server:
```mvn clean package```

The server will be waiting for incoming connection requests via HTTP port 8887. It will upgrade all valid requests to a WebSocket connection.

## Test Suite
No tests defined, yet. :*(
TODO Add jUnit tests
