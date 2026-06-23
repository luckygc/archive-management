FROM postgres:18

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl; \
    curl -fsSL https://repo.pigsty.io/pig | bash; \
    pig repo add pigsty pgdg -u; \
    pig ext install -y pg_textsearch -v 18; \
    rm -rf /var/lib/apt/lists/*
