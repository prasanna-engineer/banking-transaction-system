# ---- Stage 1: build the jar with the Maven Wrapper (JDK image, discarded afterwards) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only the build files first: Docker caches the dependency download until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests need a database, so they run in CI (and locally), not during the image build.
RUN ./mvnw -B -q package -DskipTests && cp target/banking-*.jar app.jar

# ---- Stage 2: small runtime image with only the JRE and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Never run the application as root inside the container.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8081
# Configuration (database URL, passwords, JWT secret) comes from environment variables at run time.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
