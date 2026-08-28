package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InvoicePrizeRssClientTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title><![CDATA[統一發票中獎號碼]]></title>
                <item>
                  <title><![CDATA[115年 05~06月]]></title>
                  <description><![CDATA[
                    <p>特別獎：38548029</p>
                    <p>特獎：10138845</p>
                    <p>頭獎：24121106、28589937、83663333</p>
                  ]]></description>
                </item>
                <item>
                  <title><![CDATA[115年 03～04月]]></title>
                  <description><![CDATA[
                    <p>特別獎：19531471</p>
                    <p>特獎：85941329</p>
                    <p>頭獎：07225810、20231230、83518781</p>
                    <p>增開六獎：123、045</p>
                  ]]></description>
                </item>
              </channel>
            </rss>
            """;

    @Test
    void downloadsAndParsesOfficialRss() {
        final URI rssUri = URI.create("https://invoice.etax.nat.gov.tw/invoice.xml");
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(rssUri.toString()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "invoice/1.0"))
                .andRespond(withSuccess(RSS, MediaType.APPLICATION_XML));
        final var client = new InvoicePrizeRssClient(builder.build(), rssUri);

        final List<InvoicePeriod> periods = client.fetchPeriods();

        assertThat(periods)
                .containsExactly(
                        new InvoicePeriod(
                                "115年05-06月",
                                "38548029",
                                "10138845",
                                List.of("24121106", "28589937", "83663333"),
                                List.of()),
                        new InvoicePeriod(
                                "115年03-04月",
                                "19531471",
                                "85941329",
                                List.of("07225810", "20231230", "83518781"),
                                List.of("123", "045")));
        server.verify();
    }

    @Test
    void rejectsAnItemWithoutExactlyThreeFirstPrizes() {
        final String invalid = RSS.replace("24121106、28589937、83663333", "24121106、28589937");

        assertThatThrownBy(() -> InvoicePrizeRssClient.parse(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected number of prizes");
    }

    @Test
    void rejectsXmlWithExternalEntities() {
        final String unsafe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&secret;</title><description>value</description></item></channel></rss>
                """;

        assertThatThrownBy(() -> InvoicePrizeRssClient.parse(unsafe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse invoice prize RSS");
    }
}
