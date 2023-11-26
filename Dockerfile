FROM openjdk:latest

COPY build/libs/sumka-checker-SNAPSHOT-1.0-all.jar /home/app.jar

ENTRYPOINT ["java", "-jar", "/home/app.jar"]