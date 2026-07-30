FROM public.ecr.aws/docker/library/eclipse-temurin:25-jdk AS build

WORKDIR /app

# 国内默认走阿里云 Maven，可用构建参数覆盖
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
RUN mkdir -p /root/.m2 \
    && printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">' \
      '  <mirrors><mirror>' \
      '    <id>aliyun-public</id><mirrorOf>*</mirrorOf>' \
      "    <url>${MAVEN_MIRROR_URL}</url>" \
      '  </mirror></mirrors>' \
      '</settings>' > /root/.m2/settings.xml

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src ./src
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package \
    && cp target/demo-*.jar /app/app.jar

FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre

WORKDIR /app

RUN useradd --system --create-home --uid 10001 spring \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app \
    && chown spring:spring /app

COPY --from=build --chown=spring:spring /app/app.jar /app/app.jar

USER spring
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
