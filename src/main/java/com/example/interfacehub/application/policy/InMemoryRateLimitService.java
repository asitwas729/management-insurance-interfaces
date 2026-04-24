package com.example.interfacehub.application.policy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimitService {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        long currentWindow = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(currentWindow, 0));
        synchronized (counter) {
            if (counter.window != currentWindow) {
                counter.window = currentWindow;
                counter.count = 0;
            }
            if (counter.count >= limitPerMinute) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    private static final class WindowCounter {
        private long window;
        private int count;

        private WindowCounter(long window, int count) {
            this.window = window;
            this.count = count;
        }
    }
}
