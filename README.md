# Java-Spread-Replicated-Bank

Distributed app made in Java, using the Spread toolkit, a sort of web socket.
Its purpose is to demonstrate state replication accross clients who sends different transactions without being aware of each other.

## How to run

Pre-requisites: Java 11.0.16 is needed to run the application

1. Extract zip into a folder
2. Execute the following command:

```Java -jar .\Java-Spread-Replicated-Bank.jar <server address> <account name> <number of replicas> [file name]```

3. (optional) Enter your uio username (not email) and password to start the tunnel to ifi server

Example (server ip 129.240.65.61 for tunnel): 

```java -jar .\Java-Spread-Replicated-Bank.jar 129.240.65.61 G5 1 inputfile.txt```

Note: only one execution using the tunnel can be run simultaneously on the same computer, 
use a local instance of spread daemon running on port 4803 to run multiple instances, with server address matching the daemon loopback address
