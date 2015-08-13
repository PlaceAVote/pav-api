FROM clojure

ENV :neo-url "http:localhost:7474/db/data"
ENV :neo-username "neo4j"
ENV :neo-password "password"
ENV :redis-url "http://localhost:6379"
ENV :auth-priv-key "test-resources/pav_auth_privkey.pem"
ENV :auth-priv-key-pwd "password"
ENV PORT 8080
ENV SSLPORT 8443

WORKDIR /app

COPY . /app

RUN lein deps

EXPOSE 8080 8443

CMD ["lein with-profile production run server"]
