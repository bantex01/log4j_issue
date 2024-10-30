package com.example;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;

public class App {

    // Create a Logger instance
    //private static final Logger logger = LogManager.getLogger(App.class);

    private static final org.apache.logging.log4j.Logger logger =
      LogManager.getLogger("log4j-logger");

    // Initialize the OpenTelemetry Meter and Tracer
    private final Meter meter = GlobalOpenTelemetry.get().getMeter("com.example.App");
    private final LongCounter logCounter = meter
            .counterBuilder("java_test_log_messages")
            .setDescription("Counts the number of log messages sent")
            .setUnit("messages")
            .build();

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("com.example.App");

    private static OpenTelemetry initializeOpenTelemetry() {
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .setResource(
                        Resource.getDefault().toBuilder()
                            .put(ResourceAttributes.SERVICE_NAME, "java-test")
                            .build())
                    .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(
                                OtlpGrpcLogRecordExporter.builder()
                                    .setEndpoint("http://localhost:14317")
                                    .build())
                            .build())
                    .build())
            .build();

    // Add hook to close SDK, which flushes logs
    Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

    return sdk;
    }

    public static void main(String[] args) {

    // Find OpenTelemetryAppender in log4j configuration and install openTelemetrySdk

        OpenTelemetry openTelemetry = initializeOpenTelemetry();
        OpenTelemetryAppender.install(openTelemetry);

        App app = new App();
        app.startLogging();
    }

    // Method to start the logging process using a ScheduledExecutorService
    public void startLogging() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        // Schedule the logMessage method to run every 60 seconds
        executorService.scheduleAtFixedRate(this::logMessage, 0, 60, TimeUnit.SECONDS);

        // Add a shutdown hook to gracefully shut down the executor service when the application exits
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdown));
    }

    // Method to log messages and update OpenTelemetry metrics
    public void logMessage() {
        ThreadContext.put("routing_key", "lightstep");
        logger.info("Current ThreadContext map: {}", ThreadContext.getContext());
        Attributes attributes = Attributes.builder()
                .put("log_level", "info")
                .build();

        Span iterationSpan = tracer.spanBuilder("iteration-span").startSpan();
        iterationSpan.makeCurrent();
        try {
            logger.info("This is a test log message!");
            logCounter.add(1, attributes);
            logger.info("Incremented log_messages_sent counter!");
            iterationSpan.addEvent("Log message sent and counter incremented");
        } catch (Exception e) {
            iterationSpan.setStatus(StatusCode.ERROR, "Error occurred in iteration");
            logger.error("Error in iteration", e);
        } finally {
            iterationSpan.end();
        }

        // Log messages at various levels
        logger.info("INFO level log message.");
        logger.warn("WARN level log message.");
        logger.error("ERROR level log message.");
        logger.debug("DEBUG level log message.");
        logger.trace("TRACE level log message.");
        logger.info("TEST message.");

    }
}
