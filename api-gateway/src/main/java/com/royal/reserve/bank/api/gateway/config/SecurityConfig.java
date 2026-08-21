package com.royal.reserve.bank.api.gateway.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.interfaces.RSAPublicKey;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;

/**
 * Configuration class for security settings and JWT token handling.
 */
@Configuration
@EnableWebFluxSecurity
@Getter
public class SecurityConfig {
    @Value("${encodedJwt}")
    private String encodedJwt;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    private String jwtToken;

    /**
     * Configures the security filters and rules for the server.
     *
     * @param serverHttpSecurity the ServerHttpSecurity object to configure
     * @return the configured SecurityWebFilterChain object
     * @throws IOException                if an I/O error occurs while reading the public key
     * @throws JwkException               if an error occurs while fetching the JSON Web Key from the JwkProvider
     * @throws URISyntaxException         if the configured JWK Set URI is invalid
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity)
            throws IOException, JwkException, URISyntaxException {
        final DecodedJWT jwt = JWT.decode(encodedJwt);
        RSAPublicKey publicKey = loadPublicKey(jwt);

        serverHttpSecurity
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .addFilterAt(jwtFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec -> jwtSpec.publicKey(publicKey)))
                .authorizeExchange(exchange ->
                        exchange.pathMatchers("/eureka/**", "/discovery-server/**")
                                .permitAll()
                                .anyExchange()
                                .authenticated());
        return serverHttpSecurity.build();
    }

    /**
     * Loads the RSA public key from the JSON Web Key Set (JWK Set) URL.
     *
     * @param token the DecodedJWT object representing the decoded JWT
     * @return the RSAPublicKey object loaded from the JWK Set
     * @throws JwkException            if an error occurs while fetching the JSON Web Key from the JwkProvider
     * @throws MalformedURLException  if the JWK Set URL is malformed
     * @throws URISyntaxException     if the JWK Set URI is invalid
     */
    private RSAPublicKey loadPublicKey(DecodedJWT token) throws JwkException, MalformedURLException, URISyntaxException {
        JwkProvider provider = new UrlJwkProvider(new URI(jwkSetUri).toURL());
        return (RSAPublicKey) provider.get(token.getKeyId()).getPublicKey();
    }

    /**
     * Creates a WebFilter for extracting the JWT token from the request headers.
     *
     * @return the created WebFilter object
     */
    @Bean
    WebFilter jwtFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                jwtToken = authorizationHeader.substring(7);
            }
            return chain.filter(exchange);
        };
    }
}
