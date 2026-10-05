## Linux-container build of Sentinel AV (works with the default Docker Desktop mode).
# The engine targets Windows APIs, so it is cross-compiled to sentinel-av.exe with
# MinGW-w64 and executed under Wine. Wine is an emulation layer: use it to build,
# test and demonstrate the engine, not as a substitute for native Windows.
#
#   docker build -t sentinel-av .
#   docker run --rm -v ${PWD}/samples:/data:ro sentinel-av scan /data --signatures /sentinel/signatures.example.txt
#   docker build --target management-test .

FROM debian:bookworm-slim AS engine-build
ENV DEBIAN_FRONTEND=noninteractive WINEDEBUG=-all WINEPREFIX=/root/.wine WINEARCH=win64
RUN apt-get update \
    && apt-get install -y --no-install-recommends cmake make g++-mingw-w64-x86-64-posix wine64 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY CMakeLists.txt ./
COPY include include
COPY src src
COPY tests tests
RUN cmake -S . -B build \
        -DCMAKE_SYSTEM_NAME=Windows \
        -DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc-posix \
        -DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++-posix \
        -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=ON \
        -DCMAKE_EXE_LINKER_FLAGS=-static \
        -DCMAKE_CROSSCOMPILING_EMULATOR=/usr/lib/wine/wine64 \
    && cmake --build build --parallel \
    && ctest --test-dir build --output-on-failure

FROM eclipse-temurin:25-jdk AS management-test
WORKDIR /management
COPY management ./
RUN sh ./mvnw --batch-mode test

FROM debian:bookworm-slim AS engine
ENV DEBIAN_FRONTEND=noninteractive WINEDEBUG=-all WINEPREFIX=/root/.wine WINEARCH=win64
RUN apt-get update && apt-get install -y --no-install-recommends wine64 \
    && rm -rf /var/lib/apt/lists/* \
    && /usr/lib/wine/wine64 wineboot --init || true
WORKDIR /sentinel
COPY --from=engine-build /src/build/sentinel-av.exe /sentinel/
COPY signatures.example.txt config.example.ini /sentinel/
ENTRYPOINT ["/usr/lib/wine/wine64", "/sentinel/sentinel-av.exe"]
CMD ["--help"]
