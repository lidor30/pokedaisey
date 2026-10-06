# Image for mgba_dump (headless libmgba captures/screenshots): `make capture-image`.
FROM debian:bookworm-slim
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends gcc libc6-dev libmgba-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /work
# Scripts invoke `docker run <image> -c "<cmd>"`, so bash is the entrypoint.
ENTRYPOINT ["/bin/bash"]
