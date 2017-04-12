#!/bin/sh

echo "\nURL DISCOVERED:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:DISCOVERED AND url:*merck*)"
        }
    }
}
'
echo "\nURL FETCHED:"
curl -XGET 'localhost:9200/_search?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:FETCHED AND url:*merck*)"
        }
    }
}
'
echo "\nURL FETCHED:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:FETCHED AND url:*merck*)"
        }
    }
}
'
echo "done"
exit


echo "\nURL FETCHED"
curl -XGET 'localhost:9200/_search?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string" : {
            "default_field" : "status",
            "query" : "FETCHED"
        }
    }
}
'
echo ""
