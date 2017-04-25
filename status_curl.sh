#!/bin/sh

echo "status: ALL"
curl -XGET 'localhost:9200/status/_count?pretty' -H 'Content-Type: application/json' -d'
{
        "query": {
        "query_string": {
            "query": "*"
        }
    }
}
'
exit

echo "index: ALL"
curl -XGET 'localhost:9200/index/_search?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "*"
        }
    }
}
'
exit

echo "status: DISCOVERED"
curl -XGET 'localhost:9200/status/_count?pretty' -H 'Content-Type: application/json' -d'
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {"status": "DISCOVERED"}
        }
       ]
     }
   }
}
'
echo "status: FETCHED"
curl -XGET 'localhost:9200/status/_count?pretty' -H 'Content-Type: application/json' -d'
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {"status": "FETCHED"}
        }
       ]
     }
   }
}
'
echo "done"
exit



echo "\nURL ALL:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "*"
        }
    }
}
'
echo "\nURL FETCHED:"
curl -XGET 'localhost:9200/_search?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "status: FETCHED"
        }
    }
}
'
echo "\nURL FETCHED count all:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:FETCHED)"
        }
    }
}
'

echo "\nURL FETCHED count *merck*:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:FETCHED AND url:*merck*)"
        }
    }
}
'
echo "\nURL FETCHED count *tivo*:"
curl -XGET 'localhost:9200/_count?pretty' -H 'Content-Type: application/json' -d'
{
    "query": {
        "query_string": {
            "query": "(status:FETCHED AND url:*tivo*)"
        }
    }
}
'



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
