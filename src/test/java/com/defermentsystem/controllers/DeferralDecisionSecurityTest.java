package com.defermentsystem.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.sql.DataSource;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.servlet.support.RequestDataValueProcessor;
import org.springframework.web.context.support.XmlWebApplicationContext;

public class DeferralDecisionSecurityTest {

	@Test
	public void loginAuthenticatesL001ForTheDecisionChain() throws Exception {
		XmlWebApplicationContext context = new XmlWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.setConfigLocations(new String[] { "classpath:test-repository-context.xml",
				"file:src/main/webapp/WEB-INF/spring-security.xml" });
		context.refresh();
		try {
			JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
			jdbc.update("insert into users (USER_ID, USERNAME, PASSWORD, ENABLED) values (?, ?, ?, ?)",
					1, "L001", "test-password", true);
			jdbc.update("insert into user_roles (USER_ROLE_ID, USER_ID, AUTHORITY) values (?, ?, ?)",
					1, 1, "ROLE_ADMIN");
			FilterChainProxy filter = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
			MockHttpServletRequest login = new MockHttpServletRequest("POST", "/j_spring_security_check");
			login.setServletPath("/j_spring_security_check");
			login.addParameter("j_username", "L001");
			login.addParameter("j_password", "test-password");
			MockHttpServletResponse loginResponse = new MockHttpServletResponse();
			filter.doFilter(login, loginResponse, new MockFilterChain());
			assertEquals(302, loginResponse.getStatus());
			MockHttpSession session = (MockHttpSession) login.getSession(false);
			assertNotNull(session);
			SecurityContext authenticated = (SecurityContext) session.getAttribute(
					HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
			assertNotNull(authenticated);
			assertEquals("L001", authenticated.getAuthentication().getName());

			MockHttpServletRequest get = request("GET", "/admin/deferral/modify/id/1", session);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(get, response, new MockFilterChain());
			assertEquals(200, response.getStatus());
		} finally {
			context.close();
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	public void decisionPostRequiresCsrfToken() throws Exception {
		XmlWebApplicationContext context = new XmlWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.setConfigLocations(new String[] { "classpath:test-repository-context.xml",
				"file:src/main/webapp/WEB-INF/spring-security.xml" });
		context.refresh();
		try {
			FilterChainProxy filter = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
			MockHttpSession session = new MockHttpSession();
			SecurityContext security = SecurityContextHolder.createEmptyContext();
			security.setAuthentication(new UsernamePasswordAuthenticationToken("L001", "unused",
					AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
			session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);

			MockHttpServletRequest get = request("GET", "/admin/deferral/modify/id/1", session);
			MockHttpServletResponse getResponse = new MockHttpServletResponse();
			filter.doFilter(get, getResponse, new MockFilterChain());
			assertEquals(200, getResponse.getStatus());
			CsrfToken token = (CsrfToken) get.getAttribute(CsrfToken.class.getName());
			assertNotNull(token);
			RequestDataValueProcessor processor = context.getBean("requestDataValueProcessor",
					RequestDataValueProcessor.class);
			assertEquals(token.getToken(), processor.getExtraHiddenFields(get).get(token.getParameterName()));

			MockHttpServletRequest noToken = request("POST", "/admin/deferral/modify/id/1/decision", session);
			MockHttpServletResponse denied = new MockHttpServletResponse();
			filter.doFilter(noToken, denied, new MockFilterChain());
			assertEquals(403, denied.getStatus());

			MockHttpServletRequest withToken = request("POST", "/admin/deferral/modify/id/1/decision", session);
			withToken.addParameter(token.getParameterName(), token.getToken());
			MockHttpServletResponse accepted = new MockHttpServletResponse();
			filter.doFilter(withToken, accepted, new MockFilterChain());
			assertEquals(200, accepted.getStatus());
		} finally {
			context.close();
			SecurityContextHolder.clearContext();
		}
	}

	private MockHttpServletRequest request(String method, String path, MockHttpSession session) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setServletPath(path);
		request.setSession(session);
		return request;
	}
}
