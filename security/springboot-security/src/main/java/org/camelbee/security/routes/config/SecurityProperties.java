package org.camelbee.security.routes.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for JWT security settings.
 * This class centralizes all security-related configuration for the JWT validation process.
 * It provides default values that can be overridden through application properties.
 */
@Component
@ConfigurationProperties(prefix = "camelbee.security")
@Getter
@Setter
public class SecurityProperties {

  private boolean enabled = true;
  private String issuer;
  private String audience;
  private List<String> roleClaims = new ArrayList<>();
  private List<String> scopeClaims = new ArrayList<>();
  private String algorithm = "RS256";
  private int clockSkew = 30;
}