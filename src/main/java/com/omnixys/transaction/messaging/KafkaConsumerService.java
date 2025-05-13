package com.omnixys.transaction.messaging;

import com.omnixys.transaction.models.events.TransactionEventDTO;
import com.omnixys.transaction.models.mapper.TransactionMapper;
import com.omnixys.transaction.service.TransactionWriteService;
import com.omnixys.transaction.tracing.LoggerPlus;
import com.omnixys.transaction.tracing.LoggerPlusFactory;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_ALL_RESTART_ORCHESTRATOR;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_ALL_SHUTDOWN_ORCHESTRATOR;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_ALL_START_ORCHESTRATOR;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_TRANSACTION_CREATE_PAYMENT;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_TRANSACTION_RESTART_ORCHESTRATOR;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_TRANSACTION_SHUTDOWN_ORCHESTRATOR;
import static com.omnixys.transaction.messaging.KafkaTopicProperties.TOPIC_TRANSACTION_START_ORCHESTRATOR;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ApplicationContext context;
    private final TransactionWriteService transactionWriteService;
    private final TransactionMapper transactionMapper;
    private final Tracer tracer;
    private final LoggerPlusFactory factory;
    private LoggerPlus logger() {
        return factory.getLogger(getClass());
    }

    @KafkaListener(topics = TOPIC_TRANSACTION_CREATE_PAYMENT, groupId = "${app.groupId}")
    @Observed(name = "transaction-service.write.finalize-payment")
    public void consumeFinalizePayment(ConsumerRecord<String, TransactionEventDTO> record) {
        final var headers = record.headers();
        final var newTransactionDTO = record.value();

        // ✨ 1. Extrahiere traceparent Header (W3C) oder B3 als Fallback
        final var traceParent = getHeader(headers, "traceparent");

        SpanContext linkedContext = null;
        if (traceParent != null && traceParent.startsWith("00-")) {
            String[] parts = traceParent.split("-");
            if (parts.length == 4) {
                String traceId = parts[1];
                String spanId = parts[2];
                boolean sampled = "01".equals(parts[3]);

                linkedContext = SpanContext.createFromRemoteParent(
                    traceId,
                    spanId,
                    sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
                    TraceState.getDefault()
                );
            }
        }

        // ✨ 2. Starte neuen Trace mit Link (nicht als Parent!)
        SpanBuilder spanBuilder = tracer.spanBuilder("kafka.transaction.consume")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("messaging.system", "kafka")
            .setAttribute("messaging.destination", TOPIC_TRANSACTION_CREATE_PAYMENT)
            .setAttribute("messaging.operation", "consume");

        if (linkedContext != null && linkedContext.isValid()) {
            spanBuilder.addLink(linkedContext);
        }

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {
            assert scope != null;
            logger().info("📥 Empfangene Nachricht auf '{}': {}", TOPIC_TRANSACTION_CREATE_PAYMENT, newTransactionDTO);
            final var transaction = transactionMapper.toTransaction(newTransactionDTO);
            transactionWriteService.pay(transaction, newTransactionDTO.paymentId());
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Kafka-Fehler");
            logger().error("❌ Fehler beim Erstellen des Kontos", e);
        } finally {
            span.end();
        }
    }

    private String getHeader(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    @Observed(name = "kafka-consume.transaction.orchestration")
    @KafkaListener(
        topics = {
            TOPIC_TRANSACTION_SHUTDOWN_ORCHESTRATOR,
            TOPIC_TRANSACTION_START_ORCHESTRATOR,
            TOPIC_TRANSACTION_RESTART_ORCHESTRATOR
        },
        groupId = "${app.groupId}"
    )
    public void handlePersonScoped(ConsumerRecord<String, String> record) {
        final String topic = record.topic();
        logger().info("Person-spezifisches Kommando empfangen: {}", topic);

        switch (topic) {
            case TOPIC_TRANSACTION_SHUTDOWN_ORCHESTRATOR -> shutdown();
            case TOPIC_TRANSACTION_RESTART_ORCHESTRATOR -> restart();
            case TOPIC_TRANSACTION_START_ORCHESTRATOR -> logger().info("Startsignal für Person-Service empfangen");
        }
    }

    @Observed(name = "kafka-consume.all.orchestration")
    @KafkaListener(
        topics = {
            TOPIC_ALL_SHUTDOWN_ORCHESTRATOR,
            TOPIC_ALL_START_ORCHESTRATOR,
            TOPIC_ALL_RESTART_ORCHESTRATOR
        },
        groupId = "${app.groupId}"
    )
    public void handleGlobalScoped(ConsumerRecord<String, String> record) {
        final String topic = record.topic();
        logger().info("Globales Systemkommando empfangen: {}", topic);

        switch (topic) {
            case TOPIC_ALL_SHUTDOWN_ORCHESTRATOR -> shutdown();
            case TOPIC_ALL_RESTART_ORCHESTRATOR -> restart();
            case TOPIC_ALL_START_ORCHESTRATOR -> logger().info("Globales Startsignal empfangen");
        }
    }

    private void shutdown() {
        try {
            logger().info("→ Anwendung wird heruntergefahren (Shutdown-Kommando).");
            ((ConfigurableApplicationContext) context).close();
        } catch (Exception e) {
            logger().error("Fehler beim Shutdown: {}", e.getMessage(), e);
        }
    }


    private void restart() {
        logger().info("→ Anwendung wird neugestartet (Restart-Kommando).");
        ((ConfigurableApplicationContext) context).close();
        // Neustart durch externen Supervisor erwartet
    }
}
