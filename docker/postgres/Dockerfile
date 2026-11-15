# PostgreSQL 16 with PostGIS (api-destination's places) and pgvector (its place embeddings for semantic search).
# The PostGIS image is on Debian bullseye, whose PostgreSQL packages have moved to the PostgreSQL apt archive;
# keeping PostGIS 3.4 means an existing data volume needs no extension upgrade.
FROM postgis/postgis:16-3.4

RUN sed -i 's|http://apt.postgresql.org/pub/repos/apt/|http://apt-archive.postgresql.org/pub/repos/apt/|' \
        /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-pgvector \
    && rm -rf /var/lib/apt/lists/*
