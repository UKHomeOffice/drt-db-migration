FROM openjdk:8-jre
WORKDIR /opt/docker
ADD --chown=1000 target/docker/stage/opt /opt
RUN mkdir /var/data
RUN chown 1000:1000 -R /var/data
RUN apt-get update
RUN apt-get -y install openssh-client bash procps && \
    apt-get -y install python python-pip python-setuptools ca-certificates groff less && \
    pip --no-cache-dir install awscli 
RUN cat /etc/passwd
USER 1000
ENTRYPOINT ["bin/drt-db-migration"]
CMD []
