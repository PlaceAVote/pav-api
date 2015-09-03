
## PlaceAVote User API
This API is to register new users with the site.  The API will be used register, authenticate and retrieve information
on the user.  See the postman [link][0] for how to interact with the API.

[0]: https://www.getpostman.com/collections/4f2da48d6d289bb72ea9

## Prerequisites

- [Leiningen][1] 1.7.0 or above installed.

- [Docker][2]

- [Convox][3]

[1]: https://github.com/technomancy/leiningen
[2]: https://docs.docker.com/installation/
[3]: http://convox.github.io/docs/getting-started-with-convox/

## Running

There are two ways to start the application.  

The first is to having your own instances of Neo4J and Redis running.  This is ok if your machine has a decent spec.
If you wish to install both of these then consult the following references [Redis][4], [Neo4J][5]

If both redis and neo4j are installed locally, you can start the web server by issuing the following command:

[4]:http://redis.io/topics/quickstart
[5]:http://neo4j.com/download/

    Lein ring server

The second and preferred way to launch the application locally is with the use of convox.
Convox will read the Dockerfile and docker-compose.yml files and start the server with the correct setup.  Just issue
the following command once docker and Convox are installed.

    convox start

## License

Copyright © 2015 PlaceAVote
This repository contains the User API.
