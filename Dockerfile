FROM clojure

WORKDIR /app

COPY . /app

RUN ls -ltr

ENV AUTH_PRIV_KEY=/app/test-resources/pav_auth_privkey.pem
ENV AUTH_PRIV_KEY_PWD=password
ENV AUTH_PUB_KEY=/app/test-resources/pav_auth_pubkey.pem
ENV AUTH_PUB_KEY_PWD=password
ENV PORT=8080
ENV SSLPORT=8443
ENV LEIN_ROOT=1

RUN lein uberjar
RUN cp target/pav-user-api.jar pav-user-api.jar

ENV PORT 8080

EXPOSE 8080

RUN ls -ltr
CMD java -jar pav-user-api.jar
