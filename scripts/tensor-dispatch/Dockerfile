FROM eclipse-temurin:21-jdk@sha256:4d06038800655fe1211760cd561de70ef2ed7a47f5d69255e9834414602b7026

# A Linux host avoids Bazel's Windows symlink requirement. APT verifies signed
# repository metadata and package checksums; no toolchain reaches the APK.
RUN timeout 300 apt-get update -qq && \
    DEBIAN_FRONTEND=noninteractive timeout 600 apt-get install -y -qq unzip python3 git build-essential
RUN curl -fsSL --max-time 120 -o /usr/local/bin/bazel https://github.com/bazelbuild/bazel/releases/download/7.6.1/bazel-7.6.1-linux-x86_64 && \
    echo 'ac6249d1192aea9feaf49dfee2ab50c38cee2454b00cf29bbec985a11795c025  /usr/local/bin/bazel' | sha256sum -c - && \
    chmod +x /usr/local/bin/bazel
RUN curl -fsSL --max-time 600 -o /tmp/ndk.zip https://dl.google.com/android/repository/android-ndk-r28c-linux.zip && \
    echo 'dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f  /tmp/ndk.zip' | sha256sum -c - && \
    unzip -q /tmp/ndk.zip -d /opt && rm /tmp/ndk.zip
RUN curl -fsSL --max-time 120 -o /tmp/platform.zip https://dl.google.com/android/repository/platform-36_r02.zip && \
    echo '37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e  /tmp/platform.zip' | sha256sum -c - && \
    mkdir -p /opt/android-sdk/platforms && unzip -q /tmp/platform.zip -d /opt/android-sdk/platforms && rm /tmp/platform.zip
RUN curl -fsSL --max-time 120 -o /tmp/tools.zip https://dl.google.com/android/repository/build-tools_r36_linux.zip && \
    echo '5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b  /tmp/tools.zip' | sha256sum -c - && \
    unzip -q /tmp/tools.zip -d /tmp/tools && mkdir -p /opt/android-sdk/build-tools && \
    mv /tmp/tools/android-16 /opt/android-sdk/build-tools/36.0.0 && rm /tmp/tools.zip

ENV ANDROID_HOME=/opt/android-sdk ANDROID_NDK_HOME=/opt/android-ndk-r28c
COPY sources.json fetch-sources.py google-tensor-public-hooks.patch /recipe/
RUN python3 /recipe/fetch-sources.py
WORKDIR /src/litert-lm
# Only the dispatch is built, not LiteRT-LM itself. Bazel verifies its downloaded
# repositories against their upstream SHA-256 pins.
COPY build-dispatch.sh /recipe/
RUN --mount=type=cache,id=umai-tensor-dispatch-bazel,target=/tmp/bazel-cache,sharing=locked \
    sh /recipe/build-dispatch.sh
CMD ["cp", "/artifact/libLiteRtDispatch_GoogleTensor.so", "/output/libLiteRtDispatch_GoogleTensor.so"]
