# Software Project Simulator
This is the central game server that can be deployed to and run in an infrastructure as a standard Java 11 application.

## Run the game server
To run the server:

```mvn clean package```

To build a ```*.jar``` with all dependencies in the "/target" folder:

```mvn package assembly:single```

The server will be waiting for incoming connection requests via HTTP port 80. It will upgrade all valid requests to a WebSocket connection (HTTP 101 - Switching Protocols) on the same port.

## Test Suite
No tests defined, yet. :*(

Use jUnit.