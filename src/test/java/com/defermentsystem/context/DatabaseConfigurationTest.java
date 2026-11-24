package com.defermentsystem.context;

import java.io.File;
import java.net.URL;

import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseConfigurationTest {

    @Test
    public void herokuStartsWithoutLocalProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://user:password@db.example:5432/deferment")
                .withProperty("jdbc.driverClassName", "invalid.local.Driver");
        XmlWebApplicationContext context = createContext(environment);
        try {
            context.refresh();
            DriverManagerDataSource dataSource = context.getBean("dataSource", DriverManagerDataSource.class);
            assertEquals("jdbc:postgresql://db.example:5432/deferment", dataSource.getUrl());
            assertEquals("user", dataSource.getUsername());
            assertEquals("password", dataSource.getPassword());
        } finally {
            context.close();
        }
    }

    @Test
    public void localConfigurationStillWorks() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jdbc.driverClassName", "com.mysql.jdbc.Driver")
                .withProperty("jdbc.url", "jdbc:mysql://localhost:3307/spring_project")
                .withProperty("jdbc.username", "local-user")
                .withProperty("jdbc.password", "local-password");
        XmlWebApplicationContext context = createContext(environment);
        try {
            context.refresh();
            DriverManagerDataSource dataSource = context.getBean("dataSource", DriverManagerDataSource.class);
            assertEquals("jdbc:mysql://localhost:3307/spring_project", dataSource.getUrl());
            assertEquals("local-user", dataSource.getUsername());
            assertEquals("local-password", dataSource.getPassword());
        } finally {
            context.close();
        }
    }

    @Test
    public void missingDatabaseConfigurationFailsClearly() {
        XmlWebApplicationContext context = createContext(new MockEnvironment());
        try {
            context.refresh();
            fail("Expected missing database configuration to prevent startup");
        } catch (BeanCreationException exception) {
            assertTrue(exception.getMostSpecificCause().getMessage().contains("Set DATABASE_URL"));
        } finally {
            context.close();
        }
    }

    private XmlWebApplicationContext createContext(MockEnvironment environment) {
        XmlWebApplicationContext context = new XmlWebApplicationContext();
        context.setClassLoader(new ClassLoader(getClass().getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return "prop.properties".equals(name) ? null : super.getResource(name);
            }
        });
        context.setEnvironment(environment);
        context.setServletContext(new MockServletContext(
                new File("src/main/webapp").getAbsolutePath(), new FileSystemResourceLoader()));
        context.setConfigLocation("/WEB-INF/root-context.xml");
        return context;
    }
}
