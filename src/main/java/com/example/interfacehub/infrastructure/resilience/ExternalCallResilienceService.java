package com.example.interfacehub.infrastructure.resilience;

import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionResult;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ExternalCallResilienceService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ExternalCallResilienceService(
        CircuitBreakerRegistry circuitBreakerRegistry,
        RateLimiterRegistry rateLimiterRegistry,
        BulkheadRegistry bulkheadRegistry
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    public ExecutionResult execute(
        String backendName,
        Supplier<ExecutionResult> supplier,
        ErrorCode fallbackErrorCode
    ) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(backendName);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(backendName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(backendName);

        Supplier<ExecutionResult> failureAware = () -> {
            ExecutionResult result = supplier.get();
            if (!result.success()) {
                throw new ExternalCallFailureException(result.errorCode(), result.errorMessage());
            }
            return result;
        };

        Supplier<ExecutionResult> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, failureAware);
        decorated = RateLimiter.decorateSupplier(rateLimiter, decorated);
        decorated = Bulkhead.decorateSupplier(bulkhead, decorated);

        try {
            return decorated.get();
        } catch (ExternalCallFailureException exception) {
            return ExecutionResult.failure(exception.errorCode(), exception.getMessage(), 0L);
        } catch (CallNotPermittedException exception) {
            return ExecutionResult.failure(ErrorCode.CIRCUIT_OPEN.name(), exception.getMessage(), 0L);
        } catch (RequestNotPermitted exception) {
            return ExecutionResult.failure(ErrorCode.RATE_LIMITED.name(), exception.getMessage(), 0L);
        } catch (BulkheadFullException exception) {
            return ExecutionResult.failure(ErrorCode.BULKHEAD_FULL.name(), exception.getMessage(), 0L);
        } catch (RuntimeException exception) {
            return ExecutionResult.failure(fallbackErrorCode.name(), exception.getMessage(), 0L);
        }
    }
}
