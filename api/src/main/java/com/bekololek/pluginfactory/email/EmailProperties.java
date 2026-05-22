package com.bekololek.pluginfactory.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.email")
@Getter
@Setter
public class EmailProperties {

    private boolean enabled = true;
    private String from = "bekololek@pluginfactory.org";
    private String fromName = "Plugin Factory";
    private String discordUrl = "";
}
