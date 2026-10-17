FROM eclipse-temurin:25-jre

ARG MODULE
COPY ${MODULE}/target/*.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
