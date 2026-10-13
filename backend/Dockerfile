# 微信云托管部署镜像:多阶段构建(Maven 构建 + JRE 21 运行)
# 云托管要求容器监听 80 端口(平台按 80 转发流量)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 国内构建机直连 Maven Central 不稳定(实测会报 UnresolvableModelException: 部分依赖/父 POM 解不出),
# 统一复用仓库内的阿里云镜像配置;settings 随代码分发,保证本地与云端构建源一致
COPY .mvn-aliyun-settings.xml ./maven-settings.xml
# 先拷 pom 单独下载依赖,利用 Docker 层缓存加速重复构建
COPY pom.xml .
RUN mvn -B -s /app/maven-settings.xml -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline
COPY src ./src
RUN mvn -B -s /app/maven-settings.xml -Dmaven.wagon.http.retryHandler.count=3 package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# 云托管 MySQL 同环境注入:MYSQL_ADDRESS(host:port)/MYSQL_USERNAME/MYSQL_PASSWORD
ENV PORT=80 \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 80
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT} --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-cloud}"]
