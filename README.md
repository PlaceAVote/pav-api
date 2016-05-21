## PlaceAVote API
[![CircleCI](https://circleci.com/gh/PlaceAVote/pav-api.svg?style=svg&circle-token=e5b69ee207f8360c352dfd8a269ef0aece5f0493)](https://circleci.com/gh/PlaceAVote/pav-api)
This is our core API which is response for managing our users, bills, comments and voting.

## Prerequisites

- [Leiningen][1] 1.7.0 or above installed.

- [DynamoDB] [2]

- ElasticSearch

- Redis

If you are starting application with `bin/run.sh`, these dependencies
will be automatically downloaded and started (except Leiningen and
Java). For Redis, you must have installed GCC/Clang compiler and GNU
Make tool, because Redis will be compiled from the source code.

[1]: https://github.com/technomancy/leiningen
[2]: http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Tools.DynamoDBLocal.html

## Running Locally

There are two ways to run it locally:

* running by executing `bin/run.sh` with appropriate option
* running everything manually

### Running tasks with bin/run.sh

This is automated way to get you dependencies up and running without
much of the hassle. As mentioned above, by default, it will download
all databases, setup them and start. However, Redis must be compiled
from the source code.

In case you have one of the databases installed and you'd like to skip
downloading some of them, these environment variables should be set:

* `ES_PATH` - absolute path to ElasticSearch directory. It assumes
  manually extracted ElasticSearch, not installed through package
  system like apt, rpm and etc.

* `REDIS_PATH` - absolute path to Redis directory. Location rules as
  for ElasticSearch applies here too.

* `DYNAMODB_PATH` - absolute path to DynamoDB directory. Location
  rules as for ElasticSearch applies here too.

Through `bin/run.sh` you can run application, tests, repl or
autotests, where databases will be properly started before given task
and shutdown after the task completes.

In case you'd like to start databases permanently, use
`bin/databases.sh start` and `bin/databases.sh stop` to stop them.

### Running tasks manually

You must have an instances of Dynamodb, Redis and Elasticsearch
running.

The amazon documentation above explains exactly how to do get an instance of DynamoDB running.   

You can start the web server by issuing the following command:

    lein run
    convox start //To run inside docker container.
    
The server by default starts at http://localhost:8080

## Running Tests

To run the tests, you can eiter use:

    ./bin/run.sh tests

or to rerun tests as you change the code:

    ./bin/run.sh autotests

If you have dependencies set up manually, run tests it with:
 
    lein midje

or to rerun tests as you change the code:

    lein midje :autotest
    
## Swagger Documentation

To see how to interact with the API.  See the swagger documentation at http://localhost:8080/docs.  

## License

Copyright © 2015-2016 PlaceAVote
This repository contains the User API.
