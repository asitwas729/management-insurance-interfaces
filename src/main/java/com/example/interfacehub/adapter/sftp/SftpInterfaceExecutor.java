package com.example.interfacehub.adapter.sftp;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * SFTP 어댑터 스켈레톤.
 * 실제 구현 시 Apache Commons VFS 또는 JSch 기반으로 대체한다.
 */
@Component
public class SftpInterfaceExecutor implements InterfaceExecutor {

    private final ExternalCallResilienceService resilienceService;
    private final MeterRegistry meterRegistry; // Add MeterRegistry
    private final Tracer tracer; // Add Tracer

    public SftpInterfaceExecutor(
        ExternalCallResilienceService resilienceService,
        MeterRegistry meterRegistry, // Inject MeterRegistry
        Tracer tracer // Inject Tracer
    ) {
        this.resilienceService = resilienceService;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer; // Assign Tracer
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.SFTP;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Span span = tracer.spanBuilder("SftpInterfaceExecutor.execute").startSpan();
        ExecutionResult result;
        try (Scope scope = span.makeCurrent()) {
            Timer timer = Timer.builder("execution.sftp.latency")
                .tag("interfaceCode", context.interfaceCode())
                .tag("outcome", "unknown") // Will be updated after execution
                .register(meterRegistry);

            result = executeSftpOperation(context);

            // Update span and metrics based on the result
            String outcome = result.success() ? "success" : "failure";
            timer.record(Duration.ofMillis(result.latencyMillis()));
            meterRegistry.counter("execution.sftp.count", "interfaceCode", context.interfaceCode(), "outcome", outcome).increment();

            if (result.success()) {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                span.setAttribute("execution.outcome", "success");
                span.setAttribute("execution.latency", result.latencyMillis());
            } else {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                span.setAttribute("execution.outcome", "failure");
                span.setAttribute("execution.error.code", result.errorCode());
                span.setAttribute("execution.latency", result.latencyMillis());
            }
            span.setAttribute("execution.interfaceCode", context.interfaceCode());

        } catch (Throwable t) { // Catching Throwable to include potential RuntimeExceptions from resilienceService
            // Handle exceptions from resilienceService or the lambda itself
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            span.setAttribute("execution.outcome", "failure");
            span.setAttribute("execution.error.code", ErrorCode.SFTP_TRANSFER_FAILED.name()); // Default error code for SFTP
            span.recordException(t);
            // Re-throw to ensure the orchestrator gets the exception
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(t);
        } finally {
            span.end();
        }
        return result;
    }

    private ExecutionResult executeSftpOperation(ExecutionContext context) {
        return resilienceService.execute(context.interfaceCode(), () -> {
            long start = System.currentTimeMillis(); // Use a new start time for the actual operation
            URI endpoint = URI.create(context.endpoint());
            try {
                if (!"sftp".equalsIgnoreCase(endpoint.getScheme())) {
                    return writeToLocalPath(context, endpoint, start);
                }
                return putViaSftp(context, endpoint, start);
            } catch (Exception exception) {
                return ExecutionResult.failure(ErrorCode.SFTP_TRANSFER_FAILED.name(), exception.getMessage(), elapsed(start));
            }
        }, ErrorCode.SFTP_TRANSFER_FAILED);
    }


    private ExecutionResult putViaSftp(ExecutionContext context, URI endpoint, long start) throws Exception {
        String[] auth = resolveAuth(context, endpoint);
        String username = auth[0];
        String password = auth[1];
        String host = endpoint.getHost();
        int port = endpoint.getPort() > 0 ? endpoint.getPort() : 22;
        String remoteDir = endpoint.getPath();
        String filename = resolveFilename(context);

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            session.connect((int) context.timeoutMillis());

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect((int) context.timeoutMillis());
            mkdirs(channel, remoteDir);
            channel.cd(remoteDir);
            byte[] bytes = context.payload().getBytes(StandardCharsets.UTF_8);
            channel.put(new ByteArrayInputStream(bytes), filename);
            String response = "{\"status\":\"UPLOADED\",\"remoteDir\":\"%s\",\"filename\":\"%s\"}"
                .formatted(remoteDir, filename);
            return ExecutionResult.success(response, elapsed(start));
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private ExecutionResult writeToLocalPath(ExecutionContext context, URI endpoint, long start) throws IOException {
        Path dir;
        if (endpoint.getScheme() == null || endpoint.getScheme().isBlank()) {
            dir = Paths.get(context.endpoint());
        } else if ("file".equalsIgnoreCase(endpoint.getScheme())) {
            dir = Paths.get(endpoint);
        } else {
            throw new IllegalArgumentException("Unsupported endpoint scheme for SFTP adapter: " + endpoint.getScheme());
        }
        Files.createDirectories(dir);
        String filename = resolveFilename(context);
        Path target = dir.resolve(filename);
        Files.writeString(target, context.payload(), StandardCharsets.UTF_8);
        String response = "{\"status\":\"LOCAL_WRITTEN\",\"path\":\"%s\"}".formatted(target.toAbsolutePath());
        return ExecutionResult.success(response, elapsed(start));
    }

    private String[] resolveAuth(ExecutionContext context, URI endpoint) {
        if (endpoint.getUserInfo() != null && endpoint.getUserInfo().contains(":")) {
            String[] split = endpoint.getUserInfo().split(":", 2);
            return new String[] { split[0], split[1] };
        }
        String user = context.headers().getFirst("X-SFTP-USER");
        String pass = context.headers().getFirst("X-SFTP-PASS");
        if (user == null || pass == null) {
            throw new IllegalArgumentException("SFTP auth missing. Use endpoint userinfo or X-SFTP-USER/X-SFTP-PASS headers");
        }
        return new String[] { user, pass };
    }

    private String resolveFilename(ExecutionContext context) {
        String fromHeader = context.headers().getFirst("X-FILENAME");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        return context.interfaceCode() + "-" + ts + ".json";
    }

    private void mkdirs(ChannelSftp channel, String remoteDir) throws Exception {
        String[] parts = remoteDir.split("/");
        String current = "";
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            current += "/" + part;
            try {
                channel.stat(current);
            } catch (Exception ignore) {
                channel.mkdir(current);
            }
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
