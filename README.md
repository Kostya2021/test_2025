# Software Project Simulator
This is the central game server that can be deployed to and run in an infrastructure as a standard Java 11 application.

## Run the game server
To run the server:

```mvn clean package```

To build a ```*.jar``` with all dependencies in the "/target" folder:

```mvn package assembly:single```

The server will be waiting for incoming connection requests via HTTP port 80. It will upgrade all valid requests to a WebSocket connection (HTTP 101 - Switching Protocols) on the same port.

## Test Suite
### Unit Tests
See test folder

### Load Tests
1) Install Artillery: ```npm install -g artillery```
2) Check the target URL of the running server in the config: ```/test/loadtest.yml```.
3) Start the load test: ```artillery run test\loadtest.yml -o loadTestResults.json```
4) (Optional) Generate HTML report: ```artillery report loadTestResults.json``` 
F