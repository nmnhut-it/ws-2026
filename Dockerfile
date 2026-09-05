FROM eclipse-temurin:21-jdk AS build
WORKDIR /b
COPY src ./src
COPY lib ./lib
RUN javac -encoding UTF-8 -cp lib/gamelogic.jar -d out src/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /b/out ./out
COPY --from=build /b/lib ./lib
ENV THRESHOLD=30 PORT=9000
VOLUME ["/data"]
EXPOSE 9000
CMD ["sh","-c","cd /data 2>/dev/null || cd /app; java -Dbeast.threshold=${THRESHOLD} -cp /app/out:/app/lib/gamelogic.jar GameServer ${PORT}"]
