
## PlaceAVote User API
This API is to register new users with the site.  The API will be used register, authenticate and retrieve information
on the user.

## Prerequisites

- [Leiningen][1] 1.7.0 or above installed.

- [DynamoDB] [2]

- ElasticSearch

- Redis

[1]: https://github.com/technomancy/leiningen
[2]: http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Tools.DynamoDBLocal.html

## Running Locally

The API has a few dependencies in order to run locally.  You must have an instances of Dynamodb, Redis and Elasticsearch running.

The amazon documentation above explains exactly how to do get an instance of DynamoDB running.   

You can start the web server by issuing the following command:

    Lein run
    
    convox start  //To run inside docker container.
    
The server by default starts at http://localhost:8080

## Running Tests

To run the tests.  This has a requirement on a local instance of DynamoDB running.
 
    Lein midje
    
    lein midje :autotest ;;rerun tests as you change code.
    
## Swagger Documentation

To see how to interact with the API.  See the swagger documentation at http://localhost:8080/docs.  

## License

Copyright © 2015 PlaceAVote
This repository contains the User API.
