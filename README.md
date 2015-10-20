
## PlaceAVote User API
This API is to register new users with the site.  The API will be used register, authenticate and retrieve information
on the user.

## Prerequisites

- [Leiningen][1] 1.7.0 or above installed.

- [DynamoDB] [2]

[1]: https://github.com/technomancy/leiningen
[2]: http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Tools.DynamoDBLocal.html

## Running Locally

The API has a dependency on a local instance of DynamoDB running.  The amazon documentation above explains exactly how to do this.   

You can start the web server by issuing the following command:

    Lein run
    
The server by default starts at http://localhost:8080

## Running Tests

To run the tests.  This has a requirement on a local instance of DynamoDB running.
 
    Lein midje
    
## Swagger Documentation

To see how to interact with the API.  See the swagger documentation at http://localhost:8080/docs.  

## License

Copyright © 2015 PlaceAVote
This repository contains the User API.
