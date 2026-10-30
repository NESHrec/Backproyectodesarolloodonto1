FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY clinica-serena-api.jar /app/clinica-serena-api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/clinica-serena-api.jar"]
