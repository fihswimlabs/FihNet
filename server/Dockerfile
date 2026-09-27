# FihNet backend image.
#
# Deployed directly by Render:
#   Runtime: Docker
#   Dockerfile path: server/Dockerfile
#   Build context:   server
#   Health check:    /api/health
#
# Render injects its own PORT env var, which this server reads automatically,
# and Render terminates HTTPS for us. Caddy and docker-compose.yml are therefore
# used only for self-hosted / VPS deployments and are ignored by Render.
#
# Single stage on purpose: it removes any dependency on a separate JRE image tag.
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY FihNetServer.java ApiTest.java ./
RUN javac -d /app/out FihNetServer.java ApiTest.java
# Ephemeral on Render free tier - the planned follow-up moves persistence to a
# free-tier Postgres (Neon or Supabase) so this container stays stateless.
ENV FIHNET_DATA=/data/fihnet-data.json
EXPOSE 8765
CMD ["java", "-cp", "/app/out", "FihNetServer"]
