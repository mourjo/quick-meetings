package me.mourjo.quickmeetings.web;

import java.util.List;

import org.apache.catalina.Container;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.accept.FixedContentNegotiationStrategy;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .ignoreAcceptHeader(true)
            .defaultContentType(MediaType.APPLICATION_JSON)
            .strategies(List.of(new FixedContentNegotiationStrategy(MediaType.APPLICATION_JSON)))
        ;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonErrorReportValveCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            Container host = context.getParent();
            host.getPipeline().addValve(new JsonErrorReportValve());
        });
    }
}