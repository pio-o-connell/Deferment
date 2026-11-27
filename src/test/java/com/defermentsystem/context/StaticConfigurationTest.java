package com.defermentsystem.context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertTrue;

public class StaticConfigurationTest {
    private static final String WEB = "src/main/webapp/WEB-INF/";
    private static final String ROOT = WEB + "root-context.xml";
    private static final String CHILD = WEB + "mvc-dispatcher-servlet.xml";
    private static final String SECURITY = WEB + "spring-security.xml";
    private static final String REPOSITORY = "src/main/resources/repository-context.xml";
    private static final String DESCRIPTOR = WEB + "web.xml";
    private final List<String> failures = new ArrayList<String>();

    @Test
    public void checkActualConfiguration() throws Exception {
        System.out.println("\n========== STATIC CONFIGURATION ==========");
        Document web = read(DESCRIPTOR);
        Document root = read(ROOT);
        Document child = read(CHILD);
        Document security = read(SECURITY);
        Document repository = read(REPOSITORY);

        if (web != null) {
            equal("Root configuration locations (" + DESCRIPTOR + ")",
                    set("/WEB-INF/root-context.xml", "/WEB-INF/spring-security.xml"),
                    tokens(texts(web, "//*[local-name()='context-param'][*[local-name()='param-name' and normalize-space(.)='contextConfigLocation']]/*[local-name()='param-value']")));
            equal("Root context listener (" + DESCRIPTOR + ")",
                    Collections.singletonList("org.springframework.web.context.ContextLoaderListener"),
                    texts(web, "//*[local-name()='listener']/*[local-name()='listener-class']"));
            equal("DispatcherServlet class (" + DESCRIPTOR + ")",
                    Collections.singletonList("org.springframework.web.servlet.DispatcherServlet"),
                    texts(web, "//*[local-name()='servlet'][*[local-name()='servlet-name' and normalize-space(.)='mvc-dispatcher']]/*[local-name()='servlet-class']"));
            equal("DispatcherServlet context override (" + DESCRIPTOR + ")",
                    Collections.emptyList(),
                    texts(web, "//*[local-name()='servlet'][*[local-name()='servlet-name' and normalize-space(.)='mvc-dispatcher']]/*[local-name()='init-param'][*[local-name()='param-name' and normalize-space(.)='contextConfigLocation']]"));
            equal("DispatcherServlet registrations (" + DESCRIPTOR + ")",
                    Collections.singletonList("mvc-dispatcher"),
                    texts(web, "//*[local-name()='servlet'][*[local-name()='servlet-class' and normalize-space(.)='org.springframework.web.servlet.DispatcherServlet']]/*[local-name()='servlet-name']"));
        }
        if (root != null) {
            scan(root, ROOT, set("com.defermentsystem.service", "com.defermentsystem.repository"));
            beans(root, ROOT, map(
                    "dataSource", "com.defermentsystem.repository.HerokuDataSourceFactory",
                    "transactionManager", "org.springframework.jdbc.datasource.DataSourceTransactionManager",
                    "txTemplate", "org.springframework.transaction.support.TransactionTemplate",
                    "mailSender", "org.springframework.mail.javamail.JavaMailSenderImpl",
                    "mail", "com.defermentsystem.domain.Mail"));
            transactionConfiguration(root, ROOT);
        }
        if (child != null) {
            scan(child, CHILD, set("com.defermentsystem.controllers"));
            beans(child, CHILD, map(
                    "validator", "org.springframework.validation.beanvalidation.LocalValidatorFactoryBean",
                    "tilesViewResolver", "org.springframework.web.servlet.view.UrlBasedViewResolver",
                    "tilesConfigurer", "org.springframework.web.servlet.view.tiles3.TilesConfigurer",
                    "multipartResolver", "org.springframework.web.multipart.commons.CommonsMultipartResolver"));
            equal("MVC annotation validator (" + CHILD + ")", Collections.singletonList("validator"),
                    texts(child, "//*[local-name()='annotation-driven' and namespace-uri()='http://www.springframework.org/schema/mvc']/@validator"));
            equal("MVC annotation configuration count (" + CHILD + ")", 1,
                    nodes(child, "//*[local-name()='annotation-driven' and namespace-uri()='http://www.springframework.org/schema/mvc']").getLength());
        }
        if (security != null) {
            scan(security, SECURITY, Collections.<String>emptySet());
            beans(security, SECURITY, map(
                    "roleBasedAuthenticationSuccessHandler", "com.defermentsystem.security.RoleBasedAuthenticationSuccessHandler",
                    "bcryptPasswordEncoder", "org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder",
                    "jdbcUserDetailsService", "org.springframework.security.core.userdetails.jdbc.JdbcDaoImpl",
                    "migratingAuthenticationProvider", "com.defermentsystem.security.MigratingDaoAuthenticationProvider"));
        }
        if (repository != null) {
            scan(repository, REPOSITORY, set("com.defermentsystem.repository"));
            beans(repository, REPOSITORY, map(
                    "dataSource", "org.springframework.jdbc.datasource.DriverManagerDataSource",
                    "transactionManager", "org.springframework.jdbc.datasource.DataSourceTransactionManager",
                    "txTemplate", "org.springframework.transaction.support.TransactionTemplate"));
            transactionConfiguration(repository, REPOSITORY);
        }
        for (Document document : Arrays.asList(root, child, security, repository)) {
            if (document != null) {
                equal("Unexpected XML imports or aliases (" + document.getDocumentURI() + ")",
                        0, nodes(document, "//*[namespace-uri()='http://www.springframework.org/schema/beans' and (local-name()='import' or local-name()='alias')]").getLength());
            }
        }
        assertTrue("Static configuration failures:\n" + failures, failures.isEmpty());
    }

    private Document read(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            record("Required file " + path, false, "expected file; actual missing");
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(file);
            record("Required file " + path, true, "");
            return document;
        } catch (Exception exception) {
            record("Required file " + path, false, "expected valid XML; actual " + exception.getMessage());
            return null;
        }
    }

    private void scan(Document document, String path, Set<String> expected) throws Exception {
        NodeList scans = nodes(document, "//*[local-name()='component-scan' and namespace-uri()='http://www.springframework.org/schema/context']");
        List<String> packages = new ArrayList<String>();
        boolean simple = true;
        for (int i = 0; i < scans.getLength(); i++) {
            Element scan = (Element) scans.item(i);
            packages.addAll(tokens(Collections.singletonList(scan.getAttribute("base-package"))));
            if (scan.getAttributes().getLength() != 1 || scan.getElementsByTagNameNS("*", "*").getLength() != 0) {
                simple = false;
            }
        }
        equal("Component scans (" + path + ")", expected, new TreeSet<String>(packages));
        equal("Component scan count (" + path + ")", expected.isEmpty() ? 0 : 1, scans.getLength());
        equal("Component scan filters and options (" + path + ")", true, simple);
    }

    private void beans(Document document, String path, Map<String, String> expected) throws Exception {
        NodeList nodes = nodes(document, "//*[local-name()='bean' and namespace-uri()='http://www.springframework.org/schema/beans']");
        Map<String, String> actual = new TreeMap<String, String>();
        List<String> duplicates = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element bean = (Element) nodes.item(i);
            String id = bean.getAttribute("id");
            if (id.isEmpty()) {
                id = bean.getAttribute("name");
            }
            if (!id.isEmpty() && actual.put(id, bean.getAttribute("class")) != null) {
                duplicates.add(id);
            }
            if (id.isEmpty()) {
                duplicates.add("(anonymous bean: " + bean.getAttribute("class") + ")");
            }
        }
        equal("Explicit beans (" + path + ")", expected, actual);
        equal("Duplicate or anonymous explicit beans (" + path + ")", Collections.emptyList(), duplicates);
    }

    private void transactionConfiguration(Document document, String path) throws Exception {
        equal("Transaction manager reference (" + path + ")", Collections.singletonList("transactionManager"),
                texts(document, "//*[local-name()='annotation-driven' and namespace-uri()='http://www.springframework.org/schema/tx']/@transaction-manager"));
    }

    private NodeList nodes(Document document, String expression) throws Exception {
        return (NodeList) XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.NODESET);
    }

    private List<String> texts(Document document, String expression) throws Exception {
        NodeList nodes = nodes(document, expression);
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            values.add(node.getTextContent().trim());
        }
        return values;
    }

    private Set<String> tokens(List<String> values) {
        Set<String> result = new TreeSet<String>();
        for (String value : values) {
            for (String token : value.split("[,;\\s]+")) {
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private Set<String> set(String... values) {
        return new TreeSet<String>(Arrays.asList(values));
    }

    private Map<String, String> map(String... values) {
        Map<String, String> result = new TreeMap<String, String>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }

    private void equal(String label, Object expected, Object actual) {
        record(label, expected.equals(actual), "expected " + expected + "; actual " + actual);
    }

    private void record(String label, boolean passed, String details) {
        System.out.println((passed ? "PASS " : "FAIL ") + label + (passed ? "" : " | " + details));
        if (!passed) {
            failures.add(label + " | " + details);
        }
    }
}
