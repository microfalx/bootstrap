package net.microfalx.bootstrap.application;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.lang.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("bootstrap.application")
@Getter
@Setter
@ToString
public class ApplicationProperties {

    private String id;

    private String name = "Default";

    private String description;

    private String vendor = StringUtils.NA_STRING;

    private String url = "#";

    private String version = "1.0.0";

    private String timeZone;

    private String executable;

}
