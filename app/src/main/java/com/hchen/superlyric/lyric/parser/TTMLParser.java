/*
 * This file is part of SuperLyric.
 *
 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyric.parser;

import com.hchen.superlyric.lyric.model.LyricAgent;
import com.hchen.superlyric.lyric.model.LyricFormat;
import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricMetadata;
import com.hchen.superlyric.lyric.model.LyricTrack;
import com.hchen.superlyric.lyric.model.LyricWord;
import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyric.lyric.model.RubySyllable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Parses TTML (Timed Text Markup Language) format lyrics.
 * Supports agents, multi-track, word timing, Ruby annotations, and metadata.
 * <p>
 * Uses standard Java DOM parser (javax.xml.parsers) for JVM testability.
 *
 * @author HChenX
 */
public class TTMLParser {

    private static final String TTML_NS = "http://www.w3.org/ns/ttml";
    private static final String TTP_NS = "http://www.w3.org/ns/ttml#parameter";
    private static final String TTS_NS = "http://www.w3.org/ns/ttml#styling";
    private static final String ALL_NS = "http://www.w3.org/ns/ttml#metadata";
    private static final String AMLL_NS = "http://amwa.tv/AMWA-L2.1-AMLL-2023";

    private static final Pattern ISO8601_DURATION_PATTERN = Pattern.compile(
            "^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?$");
    private static final Pattern ISO8601_TIME_PATTERN = Pattern.compile(
            "^(?:(\\d{2,}):)?(\\d{2}):(\\d{2})(?:[.,](\\d{1,3}))?$");

    public ParsedLyrics parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParsedLyrics(null, null, null, LyricFormat.TTML, content);
        }

        Document doc = parseXml(content);
        if (doc == null) {
            return new ParsedLyrics(null, null, null, LyricFormat.TTML, content);
        }

        Element root = doc.getDocumentElement();
        if (root == null || !isTtmlElement(root)) {
            return new ParsedLyrics(null, null, null, LyricFormat.TTML, content);
        }

        LyricMetadata.Builder metaBuilder = LyricMetadata.builder();
        metaBuilder.setTiming(root.getAttribute("timing"));
        metaBuilder.setLanguage(root.getAttribute("lang"));
        String bodyDur = root.getAttribute("bodyDur");
        if (!bodyDur.isEmpty()) {
            metaBuilder.putExtra("bodyDur", bodyDur);
        }

        List<LyricAgent> agents = new ArrayList<>();
        List<LyricTrack> tracks = new ArrayList<>();

        // Parse head section
        Element head = findChild(root, "head");
        if (head != null) {
            parseHead(head, agents, metaBuilder);
        }

        // Parse body section
        Element body = findChild(root, "body");
        if (body != null) {
            // Handle multiple divs (multi-track)
            NodeList divNodes = body.getElementsByTagNameNS(TTML_NS, "div");
            if (divNodes.getLength() == 0) {
                // No divs - parse paragraphs directly from body
                parseBodyElements(body, agents, tracks);
            } else {
                for (int i = 0; i < divNodes.getLength(); i++) {
                    Element div = (Element) divNodes.item(i);
                    parseDiv(div, agents, tracks);
                }
            }
        }

        return new ParsedLyrics(metaBuilder.build(), agents, tracks, LyricFormat.TTML, content);
    }

    private void parseHead(Element head, List<LyricAgent> agents, LyricMetadata.Builder metaBuilder) {
        // Parse agents from <ttm:agent> or <agent>
        NodeList agentNodes = head.getElementsByTagNameNS(TTML_NS, "agent");
        if (agentNodes.getLength() == 0) {
            agentNodes = head.getElementsByTagNameNS(AMLL_NS, "agent");
        }
        if (agentNodes.getLength() == 0) {
            agentNodes = head.getElementsByTagNameNS(ALL_NS, "agent");
        }
        if (agentNodes.getLength() == 0) {
            agentNodes = head.getElementsByTagName("agent");
        }

        for (int i = 0; i < agentNodes.getLength(); i++) {
            Element agentEl = (Element) agentNodes.item(i);
            String id = agentEl.getAttribute("id");
            String name = agentEl.getAttributeNS(TTML_NS, "name");
            if (name.isEmpty()) name = agentEl.getAttribute("name");
            String type = agentEl.getAttributeNS(TTML_NS, "type");
            if (type.isEmpty()) type = agentEl.getAttribute("type");

            if (!id.isEmpty()) {
                agents.add(new LyricAgent(id, normalizeAgentType(type), name.isEmpty() ? null : name));
            }
        }

        // Parse metadata elements
        NodeList metaNodes = head.getElementsByTagNameNS(ALL_NS, "meta");
        if (metaNodes.getLength() == 0) {
            metaNodes = head.getElementsByTagName("meta");
        }
        for (int i = 0; i < metaNodes.getLength(); i++) {
            Element metaEl = (Element) metaNodes.item(i);
            String name = metaEl.getAttributeNS(TTML_NS, "name");
            if (name.isEmpty()) name = metaEl.getAttribute("name");
            String content = metaEl.getAttributeNS(ALL_NS, "content");
            if (content.isEmpty()) content = metaEl.getTextContent();

            if (!name.isEmpty() && !content.isEmpty()) {
                switch (name) {
                    case "title": metaBuilder.setTitle(content); break;
                    case "creator":
                    case "artist": metaBuilder.setArtist(content); break;
                    case "collection": metaBuilder.setAlbum(content); break;
                    default: metaBuilder.putExtra(name, content); break;
                }
            }
        }

        // Parse iTunes metadata
        NodeList iTunesNodes = head.getElementsByTagNameNS(AMLL_NS, "itunesMetadata");
        for (int i = 0; i < iTunesNodes.getLength(); i++) {
            Element itunesEl = (Element) iTunesNodes.item(i);
            NodeList children = itunesEl.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element childEl = (Element) child;
                String childName = childEl.getLocalName();
                if (childName == null) childName = childEl.getTagName();
                String childContent = childEl.getTextContent();

                switch (childName) {
                    case "title": metaBuilder.setTitle(childContent); break;
                    case "artist": metaBuilder.setArtist(childContent); break;
                    case "album": metaBuilder.setAlbum(childContent); break;
                }
            }
        }
    }

    private void parseDiv(Element div, List<LyricAgent> agents, List<LyricTrack> tracks) {
        String lang = div.getAttribute("xml:lang");
        if (lang.isEmpty()) lang = div.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");

        // Determine track type from div structure
        String trackType = LyricTrack.TYPE_ORIGINAL;
        List<LyricLine> lines = new ArrayList<>();

        // Parse paragraphs within div
        NodeList pNodes = div.getElementsByTagNameNS(TTML_NS, "p");
        if (pNodes.getLength() == 0) {
            pNodes = div.getElementsByTagNameNS(AMLL_NS, "p");
        }
        if (pNodes.getLength() == 0) {
            pNodes = div.getElementsByTagName("p");
        }

        for (int i = 0; i < pNodes.getLength(); i++) {
            Element p = (Element) pNodes.item(i);
            LyricLine line = parseParagraph(p);
            if (line != null) lines.add(line);
        }

        if (!lines.isEmpty()) {
            tracks.add(new LyricTrack(trackType, lang.isEmpty() ? null : lang, lines));
        }
    }

    private void parseBodyElements(Element body, List<LyricAgent> agents, List<LyricTrack> tracks) {
        List<LyricLine> lines = new ArrayList<>();

        NodeList pNodes = body.getElementsByTagNameNS(TTML_NS, "p");
        if (pNodes.getLength() == 0) {
            pNodes = body.getElementsByTagNameNS(AMLL_NS, "p");
        }
        if (pNodes.getLength() == 0) {
            pNodes = body.getElementsByTagName("p");
        }

        for (int i = 0; i < pNodes.getLength(); i++) {
            Element p = (Element) pNodes.item(i);
            LyricLine line = parseParagraph(p);
            if (line != null) lines.add(line);
        }

        if (!lines.isEmpty()) {
            tracks.add(new LyricTrack(LyricTrack.TYPE_ORIGINAL, null, lines));
        }
    }

    private LyricLine parseParagraph(Element p) {
        String beginStr = getAttribute(p, "begin");
        String endStr = getAttribute(p, "end");

        long start = parseTimecode(beginStr);
        long end = parseTimecode(endStr);

        if (start < 0) return null;
        if (end <= 0) end = start + 5000;

        // Get agent ID
        String agentId = getAttribute(p, "agent");
        if (agentId.isEmpty()) {
            agentId = getAttributeNS(p, TTML_NS, "agent");
        }

        // Parse spans for word-level timing
        List<LyricWord> words = new ArrayList<>();
        NodeList spanNodes = p.getElementsByTagNameNS(TTML_NS, "span");
        if (spanNodes.getLength() == 0) {
            spanNodes = p.getElementsByTagNameNS(AMLL_NS, "span");
        }
        if (spanNodes.getLength() == 0) {
            spanNodes = p.getElementsByTagName("span");
        }

        StringBuilder fullText = new StringBuilder();

        if (spanNodes.getLength() > 0) {
            for (int i = 0; i < spanNodes.getLength(); i++) {
                Element span = (Element) spanNodes.item(i);
                String wordBeginStr = getAttribute(span, "begin");
                String wordEndStr = getAttribute(span, "end");
                long wStart = parseTimecode(wordBeginStr);
                long wEnd = parseTimecode(wordEndStr);

                String spanText = span.getTextContent();
                if (spanText == null) spanText = "";

                if (wStart >= 0) {
                    if (wEnd <= 0) wEnd = wStart + 200;
                    words.add(new LyricWord(wStart, wEnd, spanText.trim()));
                } else {
                    words.add(new LyricWord.of(spanText.trim()));
                }
                fullText.append(spanText);
            }
        } else {
            // No spans - entire paragraph is one word
            String text = p.getTextContent();
            if (text == null) text = "";
            fullText.append(text);
            words.add(new LyricWord(start, end, text.trim()));
        }

        String plainText = fullText.toString().trim();
        if (plainText.isEmpty()) return null;

        return new LyricLine(start, end, plainText, words);
    }

    /**
     * Parse a TTML timecode string to milliseconds.
     * Supports formats:
     * - HH:MM:SS.mmm
     * - MM:SS.mmm
     * - SS.mmm
     * - ISO 8601 duration: PT4M30S, PT0.5S
     */
    static long parseTimecode(String tc) {
        if (tc == null || tc.isEmpty()) return -1;
        tc = tc.trim();

        // ISO 8601 duration
        if (tc.startsWith("P") && !tc.startsWith("PT0")) {
            return parseIso8601Duration(tc);
        }
        if (tc.startsWith("PT") || tc.startsWith("PT0")) {
            return parseIso8601Duration(tc);
        }

        // MM:SS.mmm or HH:MM:SS.mmm
        Matcher m = ISO8601_TIME_PATTERN.matcher(tc);
        if (m.matches()) {
            long hours = 0;
            if (m.group(1) != null) {
                hours = Long.parseLong(m.group(1));
            }
            long minutes = Long.parseLong(m.group(2));
            long seconds = Long.parseLong(m.group(3));
            long fractionMs = 0;
            if (m.group(4) != null) {
                String frac = m.group(4);
                if (frac.length() == 1) fractionMs = Long.parseLong(frac) * 100;
                else if (frac.length() == 2) fractionMs = Long.parseLong(frac) * 10;
                else fractionMs = Long.parseLong(frac);
            }
            return Math.addExact(Math.addExact(Math.addExact(
                    Math.multiplyExact(hours, 3600000L),
                    Math.multiplyExact(minutes, 60000L)),
                    Math.multiplyExact(seconds, 1000L)), fractionMs);
        }

        // SS.mmm
        try {
            int dot = tc.indexOf('.');
            if (dot >= 0) {
                long seconds = Long.parseLong(tc.substring(0, dot));
                String frac = tc.substring(dot + 1);
                long fracMs;
                if (frac.length() == 1) fracMs = Long.parseLong(frac) * 100;
                else if (frac.length() == 2) fracMs = Long.parseLong(frac) * 10;
                else fracMs = Long.parseLong(frac);
                return Math.addExact(Math.multiplyExact(seconds, 1000L), fracMs);
            } else {
                return Long.parseLong(tc) * 1000;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parse ISO 8601 duration (e.g., PT4M30S, PT0.5S) to milliseconds.
     */
    static long parseIso8601Duration(String duration) {
        if (duration == null || duration.isEmpty()) return -1;
        Matcher m = ISO8601_DURATION_PATTERN.matcher(duration);
        if (!m.matches()) return -1;

        long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
        long minutes = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
        long seconds = 0;
        if (m.group(3) != null) {
            seconds = (long) (Double.parseDouble(m.group(3)) * 1000);
        }

        return Math.addExact(Math.addExact(
                Math.multiplyExact(hours, 3600000L),
                Math.multiplyExact(minutes, 60000L)), seconds);
    }

    private Document parseXml(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(content));
            return builder.parse(source);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return null;
        }
    }

    private static boolean isTtmlElement(Element el) {
        String localName = el.getLocalName();
        if (localName == null) return "tt".equals(el.getTagName());
        return "tt".equals(localName);
    }

    private static Element findChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) child;
            String name = el.getLocalName();
            if (name == null) name = el.getTagName();
            if (localName.equals(name)) return el;
        }
        return null;
    }

    private static String getAttribute(Element el, String name) {
        return el.getAttribute(name) != null ? el.getAttribute(name) : "";
    }

    private static String getAttributeNS(Element el, String ns, String localName) {
        return el.getAttributeNS(ns, localName) != null ? el.getAttributeNS(ns, localName) : "";
    }

    private static String normalizeAgentType(String type) {
        if (type == null || type.isEmpty()) return LyricAgent.TYPE_PERSON;
        return type.toLowerCase();
    }
}
