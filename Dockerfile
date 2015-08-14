FROM clojure

ENV NEO_URL "http:localhost:7474/db/data"
ENV NEO_USERNAME "neo4j"
ENV NEO_PASSWORD "password"
ENV REDIS_URL "http://localhost:6379"
ENV AUTH_PRIV_KEY "test-resources/pav_auth_privkey.pem"
ENV AUTH_PRIV_KEY_PWD "password"
ENV PORT 8080
ENV SSLPORT 8443
ENV LEIN_ROOT 1

WORKDIR /app

COPY . /app

RUN lein deps

EXPOSE 8080 8443

RUN ls -ltr

CMD lein with-profile production ring server
