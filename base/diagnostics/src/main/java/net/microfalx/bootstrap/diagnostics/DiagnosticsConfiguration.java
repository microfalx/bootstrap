package net.microfalx.bootstrap.diagnostics;

import net.microfalx.bootstrap.configuration.annotation.ConfigurationMapping;
import net.microfalx.lang.annotation.DefaultValue;

import java.time.Duration;

@ConfigurationMapping(prefix = "bootstrap.diagnostics")
public interface DiagnosticsConfiguration {

    @DefaultValue("10s")
    Duration getScrapeInterval();

    @DefaultValue("5m")
    Duration getHealthInterval();
}
