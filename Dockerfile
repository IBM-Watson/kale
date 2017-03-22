FROM ibmjava:sfj-alpine
MAINTAINER Bruce Adams <ba@us.ibm.com>

ADD https://ibm.biz/kale-jar /usr/local/lib/kale.jar
RUN chmod a+r /usr/local/lib/kale.jar
COPY kale.sh /usr/local/bin/kale
