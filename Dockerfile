# Image de l'application : JRE 21, utilisateur non root, JAR construit par Maven (target/socle-testapp.jar)
FROM eclipse-temurin:21-jre

RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app
WORKDIR /app
COPY --chown=app:app target/socle-testapp.jar app.jar
USER app

EXPOSE 8080
# La mémoire du conteneur est limitée au déploiement (512 Mo) : la JVM en prend 75 %
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
