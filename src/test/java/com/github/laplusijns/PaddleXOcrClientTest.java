package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PaddleXOcrClientTest {

    @Test
    void callsPaddleXServingApiAndParsesRecognizedTexts() {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:16601");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final byte[] image = "invoice-image".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo("http://127.0.0.1:16601/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "file": "%s",
                          "fileType": 1,
                          "visualize": false
                        }
                        """.formatted(Base64.getEncoder().encodeToString(image))))
                .andRespond(withSuccess(
                        """
                        {
                          "logId": "test-log-id",
                          "errorCode": 0,
                          "errorMsg": "Success",
                          "result": {
                            "ocrResults": [
                              {
                                "prunedResult": {
                                  "rec_texts": ["電子發票證明聯", "EF-11223344", "114年11-12月"]
                                }
                              }
                            ]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        final var client = new PaddleXOcrClient(builder.build(), new InvoiceTextExtractor(), "/ocr");

        final var result = client.recognize(image);

        assertThat(result).contains(new InvoiceOcrResult("EF-11223344", "114年11-12月"));
        server.verify();
    }
}
