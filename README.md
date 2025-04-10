# Software Project Simulator
This is the central game server that can be deployed to and run in an infrastructure as a standard Java 11 application.

## Environment
The game requires a MariaDB database. The connection details can be configured in ```.env```, but will be overridden by OS environment variables. Build-time configuration is configured in ```resources/application.properties```.

## Run the Game Server
To run the server:

```mvn clean package```

To build a ```*.jar``` with all dependencies in the "/target" folder:

```mvn clean package assembly:single```

The server will be waiting for incoming connection requests via HTTP port 80. It will upgrade all valid requests to a WebSocket connection (HTTP 101 - Switching Protocols) on the same port.

## Development
Generate TypeScript interfaces in *target/typescript-generator*:

```mvn typescript-generator:generate```

### How the game server works
The server is a simple WebSocket server that listens for incoming connections on port 80. It will upgrade all valid requests to a WebSocket connection (HTTP 101 - Switching Protocols) on the same port. The server will then listen for incoming messages from the client. The client can send messages to the server in JSON format. The server will then parse the JSON and execute the corresponding method. The server will then send a response back to the client in JSON format.

For each established connection, a Game instance is created and populated with the necessary data. The Game instance is then used to handle all incoming messages from the client. After each level is finished and no players are left, the Game instance is destroyed.

Each game instance can have multiple players who play at the same time. The first levels are single-player levels, but later levels are multi-player levels.

### Simple development flow example
For the feature "Player gains experience (XP)", the following steps are needed:
* Add a new Integer field *"xp"* to the *Player* class with getters and setters.
* Extend the code where projects are finished to increment the player's xp. Create a helper method *addXP()* in Player class. 
* Add notificaiton code for the frontend
* Extend the frontend to include the new data.

## Architecture
One **GameServer** instance hosts multiple **Game** instances.
**Player**s are created when WebSocket connections are established. Players are added to the GameServer instance.

There are service classes.

Service classes are organized as Beans using Spring's dependency injection.
Service classes for **GameServer** are defined in ```config/ServerConfig.java```.
Service classes for **Game** are defined in ```config/GameConfig.java```.

## Deployment
A ```*.jar```-file with all dependencies is build and deployed to an Azure App Service (Java 21 SE runtime) via a Github Action on commit. 

The following environment variables can be defined:
- DB_URL
- DB_USER
- DB_PASSWORD
- GAME_SPEED_IN_MILLISECONDS
- LOG_LEVEL

## Test Suite
### Unit Tests
Should be written rather sooner than later for new features to get all the side effects sorted out and get your thinking as clear as water.

### Load Tests
1) Install Artillery: ```npm install -g artillery```
2) Check the target URL of the running server in the config: ```/test/loadtest.yml```.
3) Start the load test: ```artillery run test\loadtest.yml -o loadTestResults.json```
4) (Optional) Generate HTML report: ```artillery report loadTestResults.json```

## License
A licence file can be generated using ```mvn license:add-third-party```. The license file will be generated in the root directory of the project. The license file will include all third-party libraries used in the project. The license file will be generated to ```target/generated-sources/license/THIRD-PARTY.txt``` of the project. The license file will include all third-party libraries used in the project.