# patent-crawler
A web crawler based on [Storm-Crawler](http://stormcrawler.net) and [News-Crawl](https://github.com/commoncrawl/news-crawl) to gather information linking patents to products

Prerequisites
------------
* Install Apache Storm 1.0.3
* Install ElasticSearch 2.4.1
* Install Kibana 4.6.1
* Clone and compile [https://github.com/DigitalPebble/storm-crawler] with `mvn clean install`
* Start ES and Storm


Build ES indices
----------------
This has to be done everytime the topology is restarted!!

`curl -L "https://git.io/vaGkv" | bash` or `~/conf/ES_create_indices.sh`


Inject the seeds
----------------
`storm jar target/patent-crawler-1.0.jar com.digitalpebble.stormcrawler.elasticsearch.ESSeedInjector ~/patent-crawler/seeds/ feeds.txt -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local`

Check the injection: [http://localhost:9200/status/_search?pretty]


Run the topology
----------------
`storm jar target/patent-crawler-1.0.jar ch.epfl.scitas.patentcrawler.CrawlTopology -conf conf/es-conf.yaml -conf conf/crawler-conf.yaml -local`


Build & run utility
-------------------
Alternatively, you can run the following script that does all the previous steps:
`./build_and_run_patent_crawler.sh`

