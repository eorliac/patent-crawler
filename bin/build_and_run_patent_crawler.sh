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

# Set working directory to project home directory
cd "$(dirname "$0")/.."
pwd

echo "EXE: mvn clean package"
echo ""
#mvn clean package

echo "CREATE INDICES"
echo ""
pwd
./bin/ES_create_indices.sh
echo "\n\n"

echo "\n\nLAUNCHING INJECTION TOPOLOGY"
storm jar target/patent-crawler-1.0.jar com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector ~/patent-crawler/seeds/ patent-seeds.txt -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local &


echo "\n\nWAIT FOR INJECTION OF SEED URLs TO COMPLETE (Note: wait for 1 only)"
i=0
while [ $i -lt 1 ]
do
    i=$(curl --silent -XGET 'localhost:9200/status/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "*"
        }
    }
}
' | grep count | cut -d, -f 1 | cut -d: -f2)

    if [ $i==0 ]; then
       echo "  **** Still no seed URLs in spout. Will sleep for 10s.\n"
       sleep 10
    fi
done

echo "\nKILLING INJECTION TOPOLOGY (seed URL(s) found)"
####storm kill  com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector
kill -9 $(ps aux | grep '[e]lasticsearch.ESSeedInjector' | awk '{print $2}')

echo "\nLAUNCHING PATENT-CRAWLER!!"
storm jar target/patent-crawler-1.0.jar ch.epfl.scitas.patentcrawler.CrawlTopology -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local

