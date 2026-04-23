package com.example.interfacehub.application.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interfacehub.scheduler")
public class SchedulerProperties {

    private boolean enabled = true;
    private int retryFixedDelaySeconds = 60;
    private int dlqFixedDelaySeconds = 60;
    private int interfaceJobFixedDelaySeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getRetryFixedDelaySeconds() { return retryFixedDelaySeconds; }
    public void setRetryFixedDelaySeconds(int retryFixedDelaySeconds) { this.retryFixedDelaySeconds = retryFixedDelaySeconds; }

    public int getDlqFixedDelaySeconds() { return dlqFixedDelaySeconds; }
    public void setDlqFixedDelaySeconds(int dlqFixedDelaySeconds) { this.dlqFixedDelaySeconds = dlqFixedDelaySeconds; }

    public int getInterfaceJobFixedDelaySeconds() { return interfaceJobFixedDelaySeconds; }
    public void setInterfaceJobFixedDelaySeconds(int interfaceJobFixedDelaySeconds) { this.interfaceJobFixedDelaySeconds = interfaceJobFixedDelaySeconds; }
}
