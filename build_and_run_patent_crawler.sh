#!/bin/sh
#
# Utility script
#
# E. Orliac, SCITAS, EPFL
# 12/04/2017
#
# Build crawler, recreate ES indices, launch seed injection topology, kill it
# then start the patent-crawler
# ---------------------------------------------------------------------------

echo "EXE: mvn clean package"
echo ""
mvn clean package

echo "CREATE INDICES"
echo ""
./conf/ES_create_indices.sh

echo "\n\nLAUNCHING INJECTION TOPOLOGY"
storm jar target/patent-crawler-1.0.jar com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector ~/patent-crawler/seeds/ patent-seeds.txt -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local &

sleep 45

echo "\nKILLING INJECTION TOPOLOGY"
####storm kill  com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector
kill -9 $(ps aux | grep '[e]lasticsearch.ESSeedInjector' | awk '{print $2}')

echo "\nLAUNCHING PATENT-CRAWLER!!"
storm jar target/patent-crawler-1.0.jar ch.epfl.scitas.patentcrawler.CrawlTopology -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local

