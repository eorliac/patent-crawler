#!/bin/sh
#
# Utility script
#
# E. Orliac, SCITAS, EPFL
# 12/04/2017
#
# Launch seed injection topology, kill it then start the patent-crawler
# ---------------------------------------------------------------------------
# Set working directory to project home directory
cd "$(dirname "$0")/.."
pwd

###echo "EXE: mvn clean package"
###echo ""
###mvn clean package

echo "CREATE INDICES"
echo ""
./bin/ES_create_indices.sh
echo "\n\n"

seed_file="vpm30_domains.txt"
seed_dir="$HOME/patent-crawler/seeds/"

if [ ! -f "$seed_dir$seed_file" ]; then
    echo "Seed file $seed_dir$seed_file not found!"
    exit
fi
seed_number=$(wc -l $seed_dir$seed_file | cut -d " " -f 1)

echo "Seed file $seed_dir$seed_file has $seed_number entries."

echo "\n\nLAUNCHING INJECTION TOPOLOGY"
#storm jar target/patent-crawler-1.0.jar com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector ~/patent-crawler/seeds/ patent-seeds.txt -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local &
storm jar target/patent-crawler-1.0.jar com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector $seed_dir $seed_file -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local &


echo "\n\nWAIT FOR INJECTION OF SEED URLs TO COMPLETE (expected number of seed URLs: $seed_number)"
i=0
while [ $i -lt $seed_number ]
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

    if [ $i -lt $seed_number ]; then
       echo "  **** Currently $i URLs injected, out of $seed_number. Will sleep for 10s.\n"
       sleep 10
    fi
done

echo "\nKILLING INJECTION TOPOLOGY (seed URL(s) found)"
sleep 10
####storm kill  com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector
kill -9 $(ps aux | grep '[e]lasticsearch.ESSeedInjector' | awk '{print $2}')


echo "\nLAUNCHING PATENT-CRAWLER!!"
storm jar target/patent-crawler-1.0.jar ch.epfl.scitas.patentcrawler.CrawlTopology -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local

