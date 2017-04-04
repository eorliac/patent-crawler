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

/**package org.commoncrawl.news.bootstrap;**/
package ch.epfl.scitas.patentcrawler;

import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.ConfigurableTopology;
import com.digitalpebble.stormcrawler.Constants;
/**import com.digitalpebble.stormcrawler.CrawlTopology;**/

import com.digitalpebble.stormcrawler.bolt.FetcherBolt;
import com.digitalpebble.stormcrawler.bolt.JSoupParserBolt;
import com.digitalpebble.stormcrawler.bolt.URLPartitionerBolt;
import com.digitalpebble.stormcrawler.elasticsearch.persistence.AggregationSpout;
import com.digitalpebble.stormcrawler.elasticsearch.persistence.StatusUpdaterBolt;
import com.digitalpebble.stormcrawler.indexing.DummyIndexer;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.warc.WARCHdfsBolt;

/**
 * Dummy topology to play with the spouts and bolts on ElasticSearch
 */
public class BootstrapTopology extends CrawlTopology {

    private static final org.slf4j.Logger LOG = LoggerFactory
            .getLogger(BootstrapTopology.class);

    public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new BootstrapTopology(), args);
    }

    @Override
    protected int run(String[] args) {
        TopologyBuilder builder = new TopologyBuilder();

        LOG.debug("sitemap.sniffContent: {}",
                ConfUtils.getBoolean(getConf(), "sitemap.sniffContent", false));
        LOG.info("sitemap.sniffContent: {}",
                ConfUtils.getBoolean(getConf(), "sitemap.sniffContent", false));
        LOG.warn("sitemap.sniffContent: {}",
                ConfUtils.getBoolean(getConf(), "sitemap.sniffContent", false));

        int numWorkers = ConfUtils.getInt(getConf(), "topology.workers", 1);

        // set to the real number of shards ONLY if es.status.routing is set to
        // true in the configuration
        int numShards = 10;

        builder.setSpout("spout", new AggregationSpout(), numShards);

        builder.setBolt("partitioner", new URLPartitionerBolt(), numWorkers)
                .shuffleGrouping("spout");

        builder.setBolt("fetch", new FetcherBolt(), numWorkers)
                .fieldsGrouping("partitioner", new Fields("key"));

        builder.setBolt("sitemap", new PatentSiteMapDetectorBolt(), numWorkers)
                .localOrShuffleGrouping("fetch");

        builder.setBolt("feed", new FeedDetectorBolt(), numWorkers)
                .localOrShuffleGrouping("sitemap");

        builder.setBolt("parse", new JSoupParserBolt())
                .localOrShuffleGrouping("feed");

        // don't need to parse the pages but need to update their status
        builder.setBolt("ssb", new DummyIndexer(), numWorkers)
                .localOrShuffleGrouping("parse");

        WARCHdfsBolt warcbolt = getWarcBolt("CC-PATENTS-BOOTSTRAP");

        builder.setBolt("warc", warcbolt).localOrShuffleGrouping("fetch");

        builder.setBolt("status", new StatusUpdaterBolt(), numWorkers)
                .localOrShuffleGrouping("fetch", Constants.StatusStreamName)
                .localOrShuffleGrouping("sitemap", Constants.StatusStreamName)
                .localOrShuffleGrouping("feed", Constants.StatusStreamName)
                .localOrShuffleGrouping("parse", Constants.StatusStreamName)
                .localOrShuffleGrouping("ssb", Constants.StatusStreamName)
                .setNumTasks(numShards);

        return submit(conf, builder);
    }
}
