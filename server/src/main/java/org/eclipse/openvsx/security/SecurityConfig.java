/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.security;

import java.net.URI;

import org.elasticsearch.common.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@EnableWebSecurity(debug = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (Strings.isNullOrEmpty(webuiUrl) || !URI.create(webuiUrl).isAbsolute()) {
            // Default configuration: mark endpoints that require a user principal as authenticated.
            http.authorizeRequests()
                    .antMatchers("/user/tokens", "/user/token/**", "/user/namespaces", "/user/namespace/**",
                            "/user/search/**", "/api/*/*/review/**")
                    .authenticated().antMatchers("/user", "/login/**", "/logout", "/api/**", "/admin/**", "/vscode/**")
                    .permitAll();
        } else {
            // All endpoints are marked as permitted for CORS to work correctly.
            // User authentication is checked within the endpoints that require it.
            http.authorizeRequests()
                    .antMatchers("/user/**", "/login/**", "/logout", "/api/**", "/admin/**", "/vscode/**").permitAll();
        }

        if (!Strings.isNullOrEmpty(webuiUrl)) {
            // Redirect to the Web UI after login / logout
            http.oauth2Login().defaultSuccessUrl(webuiUrl, true);
            http.logout().logoutSuccessUrl(webuiUrl);
        } else {
            http.oauth2Login();
            http.logout().logoutSuccessUrl("/");
        }

        http.oauth2Login(configurer -> {
            configurer.addObjectPostProcessor(new ObjectPostProcessor<OidcAuthorizationCodeAuthenticationProvider>() {
                @Override
                public <O extends OidcAuthorizationCodeAuthenticationProvider> O postProcess(O object) {
                    object.setJwtDecoderFactory(new NoVerifyJwtDecoderFactory());
                    return object;
                }
            });
        });

        // Publishing is done only via explicit access tokens, so we don't need CSRF
        // protection here.
        http.csrf().ignoringAntMatchers("/api/-/publish", "/api/-/namespace/create", "/admin/**", "/vscode/**");
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/v2/api-docs", "/swagger-resources/**", "/swagger-ui/**", "/webjars/**");
    }

}