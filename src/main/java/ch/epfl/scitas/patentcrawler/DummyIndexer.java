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

/**package com.digitalpebble.stormcrawler.indexing;**/
package ch.epfl.scitas.patentcrawler;

import java.util.Map;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.indexing.AbstractIndexerBolt;

/***
 * Any tuple that went through all the previous bolts is sent to the status
 * stream with a Status of FETCHED. This allows the bolt in charge of storing
 * the status to rely exclusively on the status stream, as done with the real
 * indexers.
 **/
@SuppressWarnings("serial")
public class DummyIndexer extends AbstractIndexerBolt {
    OutputCollector _collector;

    @SuppressWarnings("rawtypes")
    @Override
    public void prepare(Map conf, TopologyContext context,
            OutputCollector collector) {
        super.prepare(conf, context, collector);
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {

        String url = tuple.getStringByField("url");
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");

	System.out.println("DummyIndexer %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% set to FETCHED url: " + url);
	
        _collector.emit(
                com.digitalpebble.stormcrawler.Constants.StatusStreamName,
                tuple, new Values(url, metadata, Status.FETCHED));
        _collector.ack(tuple);
    }

}
