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
ENV MYSQL_DATABASE=pav_user
ENV MYSQL_HOST=pav-user-dev.cohs9sc8kicp.us-west-2.rds.amazonaws.com
ENV MYSQL_PORT=3306
ENV MYSQL_USER=pav_user
ENV MYSQL_PASSWORD=pav_user

RUN lein uberjar
COPY target/pav-user-api.jar pav-user-api.jar

ENV PORT 8080

EXPOSE 8080

CMD ["java", "-jar", "target/pav-user-api.jar"]
