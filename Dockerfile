FROM maven:3.9-eclipse-temurin-17
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests
EXPOSE 3002
CMD ["java","-Xmx400m","-jar","target/haapp-digitalv-1.0.0.jar
