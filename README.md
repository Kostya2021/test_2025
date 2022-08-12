# Software Project Simulator
This is the central game server that can be deployed to and run in an infrastructure as a standard Java 11 application.

## Requirements
The game server requires a local MySQL database. The connection details are defined in ```resources/hibernate.cfg.xml```

## Run the Game Server
To run the server:

```mvn clean package```

To build a ```*.jar``` with all dependencies in the "/target" folder:

```mvn package assembly:single```

The server will be waiting for incoming connection requests via HTTP port 80. It will upgrade all valid requests to a WebSocket connection (HTTP 101 - Switching Protocols) on the same port.

## Development
Generate TypeScript interfaces in *target/typescript-generator*:

```mvn typescript-generator:generate```

### Simple development flow example
For the feature "Player gains experience (XP)", the following steps are needed:
* Add a new Integer field *"xp"* to the *Player* class with getters and setters. Make sure the *@JsonProperty* annotation is there for it to be serialized correctly.
* Extend the code where projects are finished to increment the player's xp. Create a helper method *addXP()* in Player class. 
* Add notificaiton code for the frontend
* Extend the frontend to include the new data.

## Test Suite
### Unit Tests
Should be written rather sooner than later for new features to get all the side effects sorted out and get your thinking as clear as water.

### Load Tests
1) Install Artillery: ```npm install -g artillery```
2) Check the target URL of the running server in the config: ```/test/loadtest.yml```.
3) Start the load test: ```artillery run test\loadtest.yml -o loadTestResults.json```
4) (Optional) Generate HTML report: ```artillery report loadTestResults.json```