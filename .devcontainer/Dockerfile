FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm@sha256:0af991049cded4b8d5a90d2614289ceec390779ad6a3e4b70957e2b4f3bb1230

USER root

ARG ANDROID_API=36
ARG ANDROID_BUILD_TOOLS=36.0.0
ARG ANDROID_COMMAND_LINE_TOOLS=15859902
ARG ANDROID_COMMAND_LINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL \
       "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_LINE_TOOLS}_latest.zip" \
       -o /tmp/android-tools.zip \
    && echo "${ANDROID_COMMAND_LINE_TOOLS_SHA256}  /tmp/android-tools.zip" | sha256sum -c - \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -r /tmp/android-tools /tmp/android-tools.zip

RUN yes | sdkmanager --licenses >/dev/null
RUN sdkmanager \
      "platform-tools" \
      "platforms;android-${ANDROID_API}" \
      "build-tools;${ANDROID_BUILD_TOOLS}" \
    && mkdir -p /home/vscode/.gradle /home/vscode/.android \
    && chown -R vscode:vscode "${ANDROID_HOME}" /home/vscode/.gradle /home/vscode/.android

USER vscode
