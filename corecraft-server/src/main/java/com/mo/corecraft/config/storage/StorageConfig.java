package com.mo.corecraft.config.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(LocalStorageProperties.class)
public class StorageConfig implements WebMvcConfigurer {

    private final LocalStorageProperties properties;

    public StorageConfig(LocalStorageProperties properties) throws Exception {
        this.properties = properties;
        Files.createDirectories(Path.of(properties.getAvatarDir()));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.getAvatarDir()).toUri().toString();
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations(location);
    }
}
