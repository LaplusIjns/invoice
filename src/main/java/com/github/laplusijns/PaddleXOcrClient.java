package com.github.laplusijns;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
class PaddleXOcrClient {

    private final RestClient restClient;
    private final InvoiceTextExtractor invoiceTextExtractor;
    private final String endpoint;

    @Autowired
    PaddleXOcrClient(
            final RestClient.Builder restClientBuilder,
            final InvoiceTextExtractor invoiceTextExtractor,
            @Value("${invoice.ocr.paddlex.base-url:http://127.0.0.1:16601}") final String baseUrl,
            @Value("${invoice.ocr.paddlex.endpoint:/ocr}") final String endpoint,
            @Value("${invoice.ocr.paddlex.connect-timeout:2s}") final Duration connectTimeout,
            @Value("${invoice.ocr.paddlex.read-timeout:60s}") final Duration readTimeout) {
        final var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(removeTrailingSlash(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.invoiceTextExtractor = invoiceTextExtractor;
        this.endpoint = normalizeEndpoint(endpoint);
    }

    PaddleXOcrClient(
            final RestClient restClient, final InvoiceTextExtractor invoiceTextExtractor, final String endpoint) {
        this.restClient = restClient;
        this.invoiceTextExtractor = invoiceTextExtractor;
        this.endpoint = normalizeEndpoint(endpoint);
    }

    Optional<InvoiceOcrResult> recognize(final byte[] imageBytes) {
        final var request = new PaddleXRequest(Base64.getEncoder().encodeToString(imageBytes), 1, false);
        final JsonNode response = restClient
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("PaddleX returned an empty response");
        }
        if (response.path("errorCode").asInt(-1) != 0) {
            throw new IllegalStateException(
                    "PaddleX OCR failed: " + response.path("errorMsg").asText("unknown error"));
        }

        final JsonNode ocrResults = response.path("result").path("ocrResults");
        if (!ocrResults.isArray()) {
            throw new IllegalStateException("PaddleX response does not contain result.ocrResults");
        }

        final List<String> recognizedTexts = new ArrayList<>();
        for (final JsonNode ocrResult : ocrResults) {
            final JsonNode texts = ocrResult.path("prunedResult").path("rec_texts");
            if (!texts.isArray()) {
                continue;
            }
            for (final JsonNode text : texts) {
                if (text.isTextual()) {
                    recognizedTexts.add(text.asText());
                }
            }
        }
        return invoiceTextExtractor.extract(recognizedTexts);
    }

    private static String removeTrailingSlash(final String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String normalizeEndpoint(final String endpoint) {
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private record PaddleXRequest(String file, int fileType, boolean visualize) {}
}
