FROM postgres:15-alpine
 
RUN apk add --no-cache \
    build-base \
    postgresql-dev \
    git
 
RUN git clone --branch v0.5.1 https://github.com/pgvector/pgvector.git /tmp/pgvector && \
    cd /tmp/pgvector && \
    make && \
    make install && \
    rm -rf /tmp/pgvector
 
RUN apk del build-base postgresql-dev git
 
COPY schema.sql /docker-entrypoint-initdb.d/schema.sql
 