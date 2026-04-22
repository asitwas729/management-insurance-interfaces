package com.example.interfacehub;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.interfacehub.application.execution.ExecutorRouter;
import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class ExecutorRouterTest {

    @Test
    void routeAndExecute_selects_matching_executor() {
        InterfaceExecutor restExecutor = new StubExecutor(ProtocolType.REST);
        ExecutorRouter router = new ExecutorRouter(List.of(restExecutor));

        ExecutionResult result = router.routeAndExecute(new ExecutionContext(
            "TEST_IF",
            ProtocolType.REST,
            "http://localhost",
            new LinkedMultiValueMap<>(),
            "{}",
            1000L
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.responsePayload()).isEqualTo("REST executed");
    }

    private record StubExecutor(ProtocolType supportType) implements InterfaceExecutor {

        @Override
        public ExecutionResult execute(ExecutionContext context) {
            return ExecutionResult.success(supportType.name() + " executed", 1L);
        }
    }
}
