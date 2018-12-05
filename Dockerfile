FROM openjdk:alpine
WORKDIR /opt/docker
ADD --chown=daemon:daenon target/docker/stage/opt /opt
RUN apk --update add openssh-client bash \
    apk --no-cache add python py-pip py-setuptools ca-certificates groff less && \
    pip --no-cache-dir install awscli && \
    rm -rf /var/cache/apk/*
USER daemon
ENTRYPOINT ["bin/drt-db-migration"]
CMD []
