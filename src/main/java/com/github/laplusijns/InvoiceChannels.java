package com.github.laplusijns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.util.concurrent.Queues;

@Component
public class InvoiceChannels {

    private static final Logger log = LoggerFactory.getLogger(InvoiceChannels.class);
    private final Map<String, Sinks.Many<InvoiceDTO>> channels = new ConcurrentHashMap<>();

    public Flux<@NonNull InvoiceDTO> invoiceSubscription(@NonNull final String jsessionid) {
        final Sinks.Many<InvoiceDTO> sink = channels.computeIfAbsent(
                jsessionid, _ -> Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false));
        return sink.asFlux();
    }

    public void createChannel(@NonNull final String jsessionid) {
        channels.computeIfAbsent(
                jsessionid, _ -> Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false));
    }

    public EmitResult tryEmitNext(@NonNull final String jsessionid, @NonNull final InvoiceDTO invoiceDTO) {
        return channels.get(jsessionid).tryEmitNext(invoiceDTO);
    }

    public void cleanUp(@NonNull final String jsessionid) {
        final Sinks.Many<InvoiceDTO> sink = channels.remove(jsessionid);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
