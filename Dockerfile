FROM eclipse-temurin:21-jdk-jammy

ARG ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    ANDROID_HOME=${ANDROID_SDK_ROOT} \
    PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip git ca-certificates make python3 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-37" "build-tools;35.0.0"

WORKDIR /workspace
COPY . .

# The image build is the dependency-provisioning step. Subsequent container runs use
# the warmed Gradle cache and the offline Makefile commands.
RUN chmod +x ./gradlew \
    && python3 tools/generate_characters.py --check \
    && ./gradlew :app:assembleDebug :app:testDebugUnitTest

CMD ["make", "test"]
