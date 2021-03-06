/**
 * Licensed to DigitalPebble Ltd under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * DigitalPebble licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
package com.digitalpebble.stormcrawler;
**/
package ch.epfl.scitas.patentcrawler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import ch.epfl.scitas.patentcrawler.FileTimeSizeRotationPolicy.Units;
import ch.epfl.scitas.patentcrawler.StatusUpdaterBolt;
    
import com.digitalpebble.stormcrawler.bolt.FetcherBolt;
/**import com.digitalpebble.stormcrawler.bolt.URLPartitionerBolt;**/
/**import com.digitalpebble.stormcrawler.elasticsearch.persistence.AggregationSpout;**/
/**import com.digitalpebble.stormcrawler.elasticsearch.persistence.StatusUpdaterBolt**/;
//import com.digitalpebble.stormcrawler.indexing.DummyIndexer;
//import com.digitalpebble.stormcrawler.elasticsearch.bolt.IndexerBolt;
import ch.epfl.scitas.patentcrawler.IndexerBolt;
import ch.epfl.scitas.patentcrawler.tika.RedirectionBolt;
import ch.epfl.scitas.patentcrawler.tika.ParserBolt;

import com.digitalpebble.stormcrawler.protocol.AbstractHttpProtocol;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.warc.WARCFileNameFormat;
import com.digitalpebble.stormcrawler.warc.WARCHdfsBolt;
import com.digitalpebble.stormcrawler.ConfigurableTopology;
import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.bolt.SiteMapParserBolt;


public class CrawlTopology extends ConfigurableTopology {

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new CrawlTopology(), args);
    }

    @Override
    protected int run(String[] args) {
        TopologyBuilder builder = new TopologyBuilder();

        int numWorkers = ConfUtils.getInt(getConf(), "topology.workers", 1);

        // set to the real number of shards ONLY if es.status.routing is set to
        // true in the configuration
        int numShards = 10;

        builder.setSpout("spout", new AggregationSpout(), numShards);

        builder.setBolt("partitioner", new URLPartitionerBolt(), numWorkers)
                .shuffleGrouping("spout");

        builder.setBolt("fetch", new FetcherBolt(), numWorkers)
                .fieldsGrouping("partitioner", new Fields("key"));

        builder.setBolt("sitemap", new PatentSiteMapParserBolt(), numWorkers)
                .setNumTasks(2).localOrShuffleGrouping("fetch");

        builder.setBolt("parse", new PatentParserBolt(), numWorkers).setNumTasks(40) // eo: set to 40 instead of 4
                .localOrShuffleGrouping("sitemap");

	builder.setBolt("shunt", new RedirectionBolt())
	    .localOrShuffleGrouping("parse");
  
	builder.setBolt("tika", new ParserBolt())
	    .localOrShuffleGrouping("shunt","tika");
	
	builder.setBolt("indexer", new IndexerBolt(), numWorkers)
	    .localOrShuffleGrouping("parse")
	    .localOrShuffleGrouping("tika");

        WARCHdfsBolt warcbolt = getWarcBolt("CC-PATENTS");
	
        builder.setBolt("warc", warcbolt)
	    .localOrShuffleGrouping("parse")
	    .localOrShuffleGrouping("tika");

        builder.setBolt("status", new StatusUpdaterBolt(), numWorkers)
                .localOrShuffleGrouping("fetch",   Constants.StatusStreamName)
                .localOrShuffleGrouping("sitemap", Constants.StatusStreamName)
	        .localOrShuffleGrouping("parse",   Constants.StatusStreamName)
	        .localOrShuffleGrouping("tika",    Constants.StatusStreamName)
	        .localOrShuffleGrouping("indexer", Constants.StatusStreamName)
	        .setNumTasks(numShards);

        return submit(conf, builder);
    }
    

    protected WARCHdfsBolt getWarcBolt(String filePrefix) {
        // path is absolute
        String warcFilePath = ConfUtils.getString(getConf(), "warc.dir",
                "/data/warc");

	System.out.println("<<<<<<<<<<<<<<<<<<<<< <<<<<<<<<< <<<<<<<<  getWarcBolt: " + warcFilePath);
	
        WARCFileNameFormat fileNameFormat = new WARCFileNameFormat();
        fileNameFormat.withPath(warcFilePath);
        fileNameFormat.withPrefix(filePrefix);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("software:", "StormCrawler 1.4 http://stormcrawler.net/");
        fields.put("description", "U.S. Patents crawler");
        String userAgent = AbstractHttpProtocol.getAgentString(getConf());
        fields.put("http-header-user-agent", userAgent);
        fields.put("http-header-from",
                ConfUtils.getString(getConf(), "http.agent.email"));
        fields.put("format", "WARC File Format 1.0");
        fields.put("conformsTo",
                "http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1_latestdraft.pdf");

        WARCHdfsBolt warcbolt = (WARCHdfsBolt) new WARCHdfsBolt()
                .withFileNameFormat(fileNameFormat);
        warcbolt.withHeader(fields);

        // will rotate if reaches 1GB or N units of time
        FileTimeSizeRotationPolicy rotpol = new FileTimeSizeRotationPolicy(1.0f,
                Units.GB);
        rotpol.setTimeRotationInterval(1,
                FileTimeSizeRotationPolicy.TimeUnit.DAYS);
        warcbolt.withRotationPolicy(rotpol);

        return warcbolt;
    }

}
