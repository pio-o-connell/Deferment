package com.defermentsystem.repository;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.PostConstruct;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class HerokuDataSourceFactory extends DriverManagerDataSource {

    private final String databaseUrl;
    private final String localDriver;
    private final String localUrl;
    private final String localUsername;
    private final String localPassword;

    public HerokuDataSourceFactory(String databaseUrl, String localDriver, String localUrl,
            String localUsername, String localPassword) {
        this.databaseUrl = databaseUrl;
        this.localDriver = localDriver;
        this.localUrl = localUrl;
        this.localUsername = localUsername;
        this.localPassword = localPassword;
    }

    @PostConstruct
    public void init() {
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            // Parse Heroku DATABASE_URL format: postgres://user:password@host:port/database
            try {
                URI dbUri = new URI(databaseUrl);
                String[] userInfo = dbUri.getUserInfo().split(":");
                String username = userInfo[0];
                String password = userInfo.length > 1 ? userInfo[1] : "";
                String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath();
                
                // Override with Heroku values
                setDriverClassName("org.postgresql.Driver");
                setUrl(dbUrl);
                setUsername(username);
                setPassword(password);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Error parsing DATABASE_URL", e);
            }
        } else {
            if (localDriver.isEmpty() || localUrl.isEmpty()) {
                throw new IllegalStateException(
                        "Set DATABASE_URL or provide jdbc.driverClassName and jdbc.url in prop.properties");
            }
            setDriverClassName(localDriver);
            setUrl(localUrl);
            setUsername(localUsername);
            setPassword(localPassword);
        }
    }
}


