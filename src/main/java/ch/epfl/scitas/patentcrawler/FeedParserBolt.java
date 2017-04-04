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

package com.digitalpebble.stormcrawler.bolt;

import static com.digitalpebble.stormcrawler.Constants.StatusStreamName;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseFilters;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.protocol.HttpHeaders;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.google.common.primitives.Bytes;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

/**
 * Extracts URLs from feeds
 */
@SuppressWarnings("serial")
public class FeedParserBolt extends StatusEmitterBolt {

    public static final String isFeedKey = "isFeed";

    private static final org.slf4j.Logger LOG = LoggerFactory
            .getLogger(FeedParserBolt.class);

    private boolean sniffWhenNoMDKey = false;

    private ParseFilter parseFilters;
    private int filterHoursSincePub = -1;

    @Override
    public void execute(Tuple tuple) {
        Metadata metadata = (Metadata) tuple.getValueByField("metadata");
        byte[] content = tuple.getBinaryByField("content");
        String url = tuple.getStringByField("url");

        boolean isfeed = Boolean.valueOf(metadata.getFirstValue(isFeedKey));
        // doesn't have the metadata expected
        if (!isfeed) {
            if (sniffWhenNoMDKey) {
                // uses mime-type
                // won't work when servers return text/xml
                // TODO use Tika instead?
                String ct = metadata.getFirstValue(HttpHeaders.CONTENT_TYPE);
                if (ct != null && ct.contains("rss+xml")) {
                    isfeed = true;
                } else {
                    // try based on the first bytes?
                    byte[] clue = "<rss ".getBytes();
                    byte[] beginning = content;
                    final int maxOffsetGuess = 100;
                    if (content.length > maxOffsetGuess) {
                        beginning = Arrays.copyOfRange(content, 0,
                                maxOffsetGuess);
                    }
                    if (Bytes.indexOf(beginning, clue) != -1) {
                        LOG.info("{} detected as rss feed based on content",
                                url);
                        isfeed = true;
                    }
                }
            }
        }

        // still not a feed file
        if (!isfeed) {
            // just pass it on
            this.collector.emit(tuple, tuple.getValues());
            this.collector.ack(tuple);
            return;
        } else {
            // can be used later on for custom scheduling
            metadata.setValue(isFeedKey, "true");
        }

        List<Outlink> outlinks;
        try {
            outlinks = parseFeed(url, content, metadata);
        } catch (Exception e) {
            // exception while parsing the feed
            String errorMessage = "Exception while parsing " + url + ": " + e;
            LOG.error(errorMessage, e);
            // send to status stream in case another component wants to update
            // its status
            metadata.setValue(Constants.STATUS_ERROR_SOURCE, "feed parsing");
            metadata.setValue(Constants.STATUS_ERROR_MESSAGE, errorMessage);
            collector.emit(Constants.StatusStreamName, tuple, new Values(url,
                    metadata, Status.ERROR));
            this.collector.ack(tuple);
            return;
        }

        // apply the parse filters if any to the current document
        try {
            ParseResult parse = new ParseResult();
            parse.setOutlinks(outlinks);
            ParseData parseData = parse.get(url);
            parseData.setMetadata(metadata);
            parseFilters.filter(url, content, null, parse);
        } catch (RuntimeException e) {
            String errorMessage = "Exception while running parse filters on "
                    + url + ": " + e;
            LOG.error(errorMessage, e);
            metadata.setValue(Constants.STATUS_ERROR_SOURCE,
                    "content filtering");
            metadata.setValue(Constants.STATUS_ERROR_MESSAGE, errorMessage);
            collector.emit(StatusStreamName, tuple, new Values(url, metadata,
                    Status.ERROR));
            collector.ack(tuple);
            return;
        }

        // send to status stream
        for (Outlink ol : outlinks) {
            Values v = new Values(ol.getTargetURL(), ol.getMetadata(),
                    Status.DISCOVERED);
            collector.emit(Constants.StatusStreamName, tuple, v);
        }

        LOG.info("Feed parser done {}", url);

        // marking the main URL as successfully fetched
        // regardless of whether we got a parse exception or not
        collector.emit(Constants.StatusStreamName, tuple, new Values(url,
                metadata, Status.FETCHED));
        this.collector.ack(tuple);
    }

    private List<Outlink> parseFeed(String url, byte[] content,
            Metadata parentMetadata) throws Exception {
        List<Outlink> links = new ArrayList<>();

        SyndFeed feed = null;
        try (ByteArrayInputStream is = new ByteArrayInputStream(content)) {
            SyndFeedInput input = new SyndFeedInput();
            feed = input.build(new InputSource(is));
        }

        URL sURL = new URL(url);

        List<SyndEntry> entries = feed.getEntries();
        for (SyndEntry entry : entries) {
            String targetURL = entry.getLink();
            // targetURL can be null?!?
            // e.g. feed does not use links but guid
            if (StringUtils.isBlank(targetURL)) {
                targetURL = entry.getUri();
                if (StringUtils.isBlank(targetURL)) {
                    continue;
                }
            }
            Outlink newLink = filterOutlink(sURL, targetURL, parentMetadata);
            if (newLink == null)
                continue;

            String title = entry.getTitle();
            if (StringUtils.isNotBlank(title)) {
                newLink.getMetadata().setValue("feed.title", title.trim());
            }

            Date publishedDate = entry.getPublishedDate();
            if (publishedDate != null) {
                // filter based on the published date
                if (filterHoursSincePub != -1) {
                    Calendar rightNow = Calendar.getInstance();
                    rightNow.add(Calendar.HOUR, -filterHoursSincePub);
                    if (publishedDate.before(rightNow.getTime())) {
                        LOG.info(
                                "{} has a published date {} which is more than {} hours old",
                                targetURL, publishedDate.toString(),
                                filterHoursSincePub);
                        continue;
                    }
                }
                newLink.getMetadata().setValue("feed.publishedDate",
                        publishedDate.toString());
            }

            SyndContent description = entry.getDescription();
            if (description != null
                    && StringUtils.isNotBlank(description.getValue())) {
                newLink.getMetadata().setValue("feed.description",
                        description.getValue());
            }

            links.add(newLink);
        }

        return links;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void prepare(Map stormConf, TopologyContext context,
            OutputCollector collect) {
        super.prepare(stormConf, context, collect);
        sniffWhenNoMDKey = ConfUtils.getBoolean(stormConf, "feed.sniffContent",
                false);
        filterHoursSincePub = ConfUtils.getInt(stormConf,
                "feed.filter.hours.since.published", -1);
        parseFilters = ParseFilters.fromConf(stormConf);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("url", "content", "metadata"));
    }

}
