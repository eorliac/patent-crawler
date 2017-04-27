FROM debian:jessie-backports

#
## Utilities
#
RUN apt-get update -qq && \
    apt-get upgrade -yq && \
    apt-get install -yq --no-install-recommends \
		apt-transport-https \
		apt-utils \
		curl \
		git-core \
		jq \
		less \
		maven \
		sudo \
		supervisor \
		wget \
		tar \
		vim \
		zookeeperd

#
## Java
#
RUN apt-get install --yes -t jessie-backports openjdk-8-jre-headless ca-certificates-java


#
## Elasticsearch
#
RUN curl http://packages.elasticsearch.org/GPG-KEY-elasticsearch | apt-key add -
RUN echo "deb http://packages.elasticsearch.org/elasticsearch/2.x/debian stable main" > /etc/apt/sources.list.d/elasticsearch.list 
RUN apt-get update  -qq \
 && apt-get install -qqy --no-install-recommends \
		elasticsearch=2.4.4

RUN ln -s /usr/share/elasticsearch/bin/elasticsearch /usr/bin/elasticsearch


#
## Kibana -- OK, but do we need it? Keep it locally for now.
#
#RUN echo "deb http://packages.elastic.co/kibana/4.6/debian stable main" | tee -a /etc/apt/sources.list.d/kibana-4.6.x.list
#RUN apt-get update -qq && \
#	apt-get install -yq --no-install-recommends \
#        	kibana
#RUN ln -s /opt/kibana/bin/kibana /usr/bin/kibana
#RUN mkdir /var/log/kibana && chown -R kibana:kibana /var/log/kibana
#RUN chown -R kibana:kibana /opt/kibana/


## install marvel, see https://www.elastic.co/downloads/marvel
#USER elasticsearch
#RUN /usr/share/elasticsearch/bin/plugin install license
#RUN /usr/share/elasticsearch/bin/plugin install marvel-agent
#USER kibana
#RUN /opt/kibana/bin/kibana plugin --install elasticsearch/marvel/latest
#RUN /opt/kibana/bin/kibana plugin --install elastic/sense
#USER root

## system configuration,
#ADD etc/sysctl.d/60-elasticsearch.conf       /etc/sysctl.d/60-elasticsearch.conf
#ADD etc/supervisor/conf.d/elasticsearch.conf /etc/supervisor/conf.d/elasticsearch.conf
#ADD etc/supervisor/conf.d/kibana.conf        /etc/supervisor/conf.d/kibana.conf
#RUN chmod -R 644 /etc/sysctl.d/60-elasticsearch.conf /etc/supervisor/conf.d/*.conf
#ENV ES_HEAP_SIZE=20g
## enable updates via scripting
#RUN echo "\n\nscript.engine.groovy.inline.update: true\n" >>/etc/elasticsearch/elasticsearch.yml



#
## Apache Storm
#
ENV STORM_VERSION=1.0.3
RUN wget -q -O - http://mirrors.ukfast.co.uk/sites/ftp.apache.org/storm/apache-storm-$STORM_VERSION/apache-storm-$STORM_VERSION.tar.gz \
	| tar -xzf - -C /opt
ENV STORM_HOME /opt/apache-storm-$STORM_VERSION
RUN groupadd storm && \
	useradd --gid storm --home-dir /home/storm \
			--create-home --shell /bin/bash storm && \
	chown -R storm:storm $STORM_HOME && \
	mkdir /var/log/storm && \
	chown -R storm:storm /var/log/storm
RUN ln -s /var/log/storm $STORM_HOME/logs
RUN ln -s $STORM_HOME/bin/storm /usr/bin/storm
#ADD etc/supervisor/conf.d/storm-*.conf   /etc/supervisor/conf.d/
#ADD etc/supervisor/conf.d/zookeeper.conf /etc/supervisor/conf.d/
#RUN chmod -R 644 /etc/supervisor/conf.d/*.conf


#
## Storm crawler
#

ENV USER=crawler

RUN groupadd $USER && \
	useradd --gid $USER --home-dir /home/$USER \
			--create-home --shell /bin/bash $USER && \
	chown -R $USER:$USER /home/$USER
USER $USER
WORKDIR /home/$USER

#
## Patent crawler
#
ENV PCDIR=patent-crawler

RUN mkdir $PCDIR/ && \
    mkdir $PCDIR/conf/ && \
    mkdir $PCDIR/seeds/ && \
    mkdir $PCDIR/bin/ && \
    mkdir $PCDIR/target/ && \
    chmod -R a+rx $PCDIR/

# add the patent crawler uber-jar
ADD target/patent-crawler-1.0.jar $PCDIR/target/

# and configuration files
ADD conf/*.*               $PCDIR/conf/
ADD seeds/patent-seeds.txt $PCDIR/seeds/
ADD bin/*.sh               $PCDIR/bin/

USER root
RUN chown -R $USER:$USER /home/$USER && \
	chmod -R a+r /home/$USER


#
## Ports
#
#  8080 - Storm UI
#  9200 - Elasticsearch http
#  9300 - Elasticsearch java
#  5601 - Kibana
EXPOSE 8080 9200 9300 5601

#
## volumes for persistent data
#
USER root
RUN mkdir /data
RUN mkdir /data/elasticsearch && chown elasticsearch:elasticsearch /data/elasticsearch
VOLUME ["/data/elasticsearch"]
RUN mkdir /data/warc && chown storm:storm /data/warc
VOLUME ["/data/warc"]

# start all services
CMD ["/usr/bin/supervisord"]

# launch the crawl
CMD ["/home/$USER/$PCDIR/bin/run_patent_crawler.sh"]
