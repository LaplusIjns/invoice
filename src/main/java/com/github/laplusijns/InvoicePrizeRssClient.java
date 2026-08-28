package com.github.laplusijns;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
class InvoicePrizeRssClient implements InvoicePeriodSource {

    private static final Pattern PERIOD_TITLE =
            Pattern.compile("(\\d{2,3})年\\s*(\\d{1,2})\\s*[~～-]\\s*(\\d{1,2})月");
    private static final Pattern SPECIAL_PRIZE = Pattern.compile("特別獎\\s*[：:]\\s*(\\d{8})");
    private static final Pattern GRAND_PRIZE = Pattern.compile("(?<!別)特獎\\s*[：:]\\s*(\\d{8})");
    private static final Pattern FIRST_PRIZES = Pattern.compile("頭獎\\s*[：:]\\s*([^<]+)");
    private static final Pattern ADDITIONAL_SIXTH_PRIZES = Pattern.compile("增開六獎\\s*[：:]\\s*([^<]+)");
    private static final Pattern EIGHT_DIGITS = Pattern.compile("\\d{8}");
    private static final Pattern THREE_DIGITS = Pattern.compile("(?<!\\d)\\d{3}(?!\\d)");

    private final RestClient restClient;
    private final URI rssUri;

    InvoicePrizeRssClient(
            @Value("${invoice.prizes.rss-url:https://invoice.etax.nat.gov.tw/invoice.xml}") final URI rssUri,
            @Value("${invoice.prizes.connect-timeout:5s}") final Duration connectTimeout,
            @Value("${invoice.prizes.read-timeout:15s}") final Duration readTimeout) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.rssUri = rssUri;
    }

    InvoicePrizeRssClient(final RestClient restClient, final URI rssUri) {
        this.restClient = restClient;
        this.rssUri = rssUri;
    }

    @Override
    public List<InvoicePeriod> fetchPeriods() {
        final byte[] xml = restClient
                .get()
                .uri(rssUri)
                .accept(MediaType.APPLICATION_XML)
                .header(HttpHeaders.USER_AGENT, "invoice/1.0")
                .retrieve()
                .body(byte[].class);
        if (xml == null || xml.length == 0) {
            throw new IllegalStateException("Invoice prize RSS returned an empty response");
        }
        return parse(xml);
    }

    static List<InvoicePeriod> parse(final String xml) {
        return parse(new InputSource(new StringReader(xml)));
    }

    private static List<InvoicePeriod> parse(final byte[] xml) {
        return parse(new InputSource(new ByteArrayInputStream(xml)));
    }

    private static List<InvoicePeriod> parse(final InputSource inputSource) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            final var document = factory.newDocumentBuilder().parse(inputSource);
            final NodeList itemNodes = document.getElementsByTagName("item");
            if (itemNodes.getLength() == 0) {
                throw new IllegalArgumentException("Invoice prize RSS contains no items");
            }

            final List<InvoicePeriod> periods = new ArrayList<>(itemNodes.getLength());
            final var seenPeriods = new LinkedHashSet<String>();
            for (int index = 0; index < itemNodes.getLength(); index++) {
                final Element item = (Element) itemNodes.item(index);
                final String title = requiredElementText(item, "title");
                final String description = requiredElementText(item, "description");
                final String period = parsePeriod(title);
                if (!seenPeriods.add(period)) {
                    throw new IllegalArgumentException("Duplicate invoice period in RSS: " + period);
                }

                periods.add(new InvoicePeriod(
                        period,
                        requiredNumber(description, SPECIAL_PRIZE, "special prize"),
                        requiredNumber(description, GRAND_PRIZE, "grand prize"),
                        prizeNumbers(description, FIRST_PRIZES, EIGHT_DIGITS, 3, true),
                        prizeNumbers(description, ADDITIONAL_SIXTH_PRIZES, THREE_DIGITS, 0, false)));
            }
            return List.copyOf(periods);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse invoice prize RSS", e);
        }
    }

    private static String requiredElementText(final Element parent, final String tagName) {
        final NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() != 1) {
            throw new IllegalArgumentException("RSS item must contain one " + tagName);
        }
        final String value = nodes.item(0).getTextContent();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RSS item contains an empty " + tagName);
        }
        return value;
    }

    private static String parsePeriod(final String title) {
        final Matcher matcher = PERIOD_TITLE.matcher(title);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid invoice period title: " + title);
        }
        final int year = Integer.parseInt(matcher.group(1));
        final int startMonth = Integer.parseInt(matcher.group(2));
        final int endMonth = Integer.parseInt(matcher.group(3));
        if (year <= 0 || startMonth < 1 || startMonth > 11 || startMonth % 2 == 0 || endMonth != startMonth + 1) {
            throw new IllegalArgumentException("Invalid invoice period title: " + title);
        }
        return "%d年%02d-%02d月".formatted(year, startMonth, endMonth);
    }

    private static String requiredNumber(
            final String description, final Pattern fieldPattern, final String fieldName) {
        final Matcher matcher = fieldPattern.matcher(description);
        if (!matcher.find()) {
            throw new IllegalArgumentException("RSS item is missing a valid " + fieldName);
        }
        return matcher.group(1);
    }

    private static List<String> prizeNumbers(
            final String description,
            final Pattern fieldPattern,
            final Pattern numberPattern,
            final int requiredCount,
            final boolean required) {
        final Matcher fieldMatcher = fieldPattern.matcher(description);
        if (!fieldMatcher.find()) {
            if (required) {
                throw new IllegalArgumentException("RSS item is missing prize numbers");
            }
            return List.of();
        }

        final List<String> numbers = new ArrayList<>();
        final Matcher numberMatcher = numberPattern.matcher(fieldMatcher.group(1));
        while (numberMatcher.find()) {
            numbers.add(numberMatcher.group());
        }
        if (requiredCount > 0 && numbers.size() != requiredCount) {
            throw new IllegalArgumentException("RSS item has an unexpected number of prizes");
        }
        return List.copyOf(numbers);
    }
}
