FROM clojure

WORKDIR /app

COPY resources /app/resources
COPY target/pav-user-api.jar pav-user-api.jar

RUN ls -ltr

CMD java -jar pav-user-api.jar