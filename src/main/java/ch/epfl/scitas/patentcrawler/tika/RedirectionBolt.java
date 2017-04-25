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

//package com.digitalpebble.stormcrawler.tika;
package ch.epfl.scitas.patentcrawler.tika;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import com.digitalpebble.stormcrawler.Metadata;

/**
 * Uses Tika only if no text found in tuple e.g. mimetype not supported by
 * JSoupParser. Emits the tuples to be processed with Tika on a stream of the
 * same name ('tika').
 * 
 * Remember to set
 * 
 * <pre>
 *   jsoup.treat.non.html.as.error: false
 * </pre>
 * 
 * Use in your topologies as follows :
 * 
 * <pre>
 * builder.setBolt(&quot;jsoup&quot;, new JSoupParserBolt()).localOrShuffleGrouping(
 *         &quot;sitemap&quot;);
 * 
 * builder.setBolt(&quot;shunt&quot;, new RedirectionBolt()).localOrShuffleGrouping(&quot;jsoup&quot;);
 * 
 * builder.setBolt(&quot;tika&quot;, new ParserBolt()).localOrShuffleGrouping(&quot;shunt&quot;,
 *         &quot;tika&quot;);
 * 
 * builder.setBolt(&quot;indexer&quot;, new IndexingBolt(), numWorkers)
 *         .localOrShuffleGrouping(&quot;shunt&quot;).localOrShuffleGrouping(&quot;tika&quot;);
 * </pre>
 */
@SuppressWarnings("serial")
public class RedirectionBolt extends BaseRichBolt {

    private OutputCollector collector;

    public void prepare(Map conf, TopologyContext context,
            OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String url = tuple.getStringByField("url");
        byte[] content = tuple.getBinaryByField("content");
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");
        String text = tuple.getStringByField("text");

        Values v = new Values(url, content, metadata, text);

        // if there is a text - no need to parse it again
        if (StringUtils.isNotBlank(text)) {
            collector.emit(tuple, v);
        } else {
            collector.emit("tika", tuple, v);
        }

        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata", "text"));
        declarer.declareStream("tika", new Fields("url", "content", "metadata",
                "text"));
    }

}
