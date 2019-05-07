FROM openjdk:8u181-jre-stretch
VOLUME /tmp
RUN apt-get update && apt-get install -y rtl-sdr sox && mkfifo /tmp/sdr-random
ARG application
ADD target/${application}.jar /app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
