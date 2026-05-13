# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — BUILD
# Uses Maven + JDK 17 to compile and package the fat JAR.
# This stage is discarded after build; only the JAR is kept.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies — this layer is cached
# as long as pom.xml doesn't change (speeds up rebuilds significantly).
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build (tests skipped to keep image build fast)
COPY src ./src
RUN mvn package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — RUNTIME
# Lean JRE-only image — much smaller than the full JDK (~200 MB vs ~600 MB).
# Render free tier has 512 MB RAM; keeping the image small matters.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user for security (good practice on any platform)
RUN addgroup -S ramira && adduser -S ramira -G ramira

# Copy only the fat JAR from the builder stage
# ⚠️  If your artifactId or version differs, update the filename below.
#     Check target/ after running `mvn package` — the JAR name is:
#     <artifactId>-<version>.jar  (e.g. RamiraFoods-MS-0.0.1-SNAPSHOT.jar)
COPY --from=builder /app/target/*.jar app.jar

# Give ownership to the non-root user
RUN chown ramira:ramira app.jar
USER ramira

# Render free tier is memory-constrained (512 MB).
# These JVM flags keep heap usage low and prevent OOM kills:
#   -Xms64m        start with 64 MB heap (don't pre-allocate too much)
#   -Xmx256m       hard cap at 256 MB heap (leaves room for metaspace + OS)
#   -XX:+UseSerialGC   Serial GC has lower overhead than G1 on small heaps
#   -Djava.security.egd  faster startup (avoids blocking on /dev/random)
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# Render injects PORT env var automatically; Spring reads server.port from it.
# Default to 8080 for local Docker runs.
ENV PORT=8080
ENV SERVER_PORT=${PORT}

EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
