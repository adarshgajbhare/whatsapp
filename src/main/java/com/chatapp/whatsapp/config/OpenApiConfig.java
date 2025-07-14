package com.chatapp.whatsapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI myOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:" + serverPort);
        devServer.setDescription("Server URL in Development environment");

        Contact contact = new Contact();
        contact.setName("WhatsApp Clone API");
        contact.setEmail("support@whatsappclone.com");
        contact.setUrl("https://www.whatsappclone.com");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("WhatsApp Clone API")
                .version("1.0")
                .contact(contact)
                .description("This API exposes endpoints for the WhatsApp Clone application.")
                .license(mitLicense);

        return new OpenAPI()
                .openapi("3.0.1")  // Explicitly set OpenAPI version
                .info(info)
                .servers(List.of(devServer));
    }
}
