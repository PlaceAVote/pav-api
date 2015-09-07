
## PlaceAVote User API
This API is to register new users with the site.  The API will be used register, authenticate and retrieve information
on the user.  See the postman [link][0] for how to interact with the API.

[0]: https://www.getpostman.com/collections/4f2da48d6d289bb72ea9

## Prerequisites

- [Leiningen][1] 1.7.0 or above installed.

- [MySQL] [2]

- [Docker][3]

- [Convox][4]

[1]: https://github.com/technomancy/leiningen
[2]: http://www.mysql.com/
[3]: https://docs.docker.com/installation/
[4]: http://convox.github.io/docs/getting-started-with-convox/

## Running

There are two ways to start the application.  

#### Method 1
The first is to having your own instances mysql running, you can start the web server by issuing the following command:

    Lein ring server
    
This will run a flyway migration script and create the tables in a schema called pav_user.  Please ensure this schema exists
first.  If you want to use a different user other than root then update the project.clj file.


#### Method 2
The second and preferred way to launch the application locally is with the use of convox.
Convox will read the Dockerfile and docker-compose.yml files and start the server with the correct setup.  Just issue
the following command once docker and Convox are installed.  This will point to a database hosted in AWS.  If you want to use a local
database instead then see method 1.

    convox start

## License

Copyright © 2015 PlaceAVote
This repository contains the User API.
