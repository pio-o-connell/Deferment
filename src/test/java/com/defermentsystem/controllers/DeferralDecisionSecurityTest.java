package com.defermentsystem.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.sql.DataSource;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
					1, 1, "ROLE_LECTURER");
			FilterChainProxy filter = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
			MockHttpServletRequest loginPage = new MockHttpServletRequest("GET", "/login");
			loginPage.setServletPath("/login");
			filter.doFilter(loginPage, new MockHttpServletResponse(), new MockFilterChain());
			CsrfToken loginToken = (CsrfToken) loginPage.getAttribute(CsrfToken.class.getName());
			assertNotNull(loginToken);
			MockHttpSession loginSession = (MockHttpSession) loginPage.getSession(true);
			loginSession.setAttribute(
					"org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN",
					loginToken);
			MockHttpServletRequest login = new MockHttpServletRequest("POST", "/j_spring_security_check");
			login.setServletPath("/j_spring_security_check");
			login.setSession(loginSession);
			login.addParameter("j_username", "L001");
			login.addParameter("j_password", "test-password");
			login.addParameter(loginToken.getParameterName(), loginToken.getToken());
			MockHttpServletResponse loginResponse = new MockHttpServletResponse();
			filter.doFilter(login, loginResponse, new MockFilterChain());
			assertEquals("Login response: " + loginResponse.getContentAsString(), 302, loginResponse.getStatus());
			MockHttpSession session = (MockHttpSession) login.getSession(false);
			assertNotNull(session);
			SecurityContext authenticated = (SecurityContext) session.getAttribute(
					HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
			assertNotNull(authenticated);
			assertEquals("L001", authenticated.getAuthentication().getName());
			assertTrue(authenticated.getAuthentication().getAuthorities().contains(
					AuthorityUtils.createAuthorityList("ROLE_LECTURER").get(0)));
			String upgradedPassword = jdbc.queryForObject(
					"select password from users where username=?", String.class, "L001");
			assertTrue(new BCryptPasswordEncoder().matches("test-password", upgradedPassword));
			assertEquals("/lecturer/dashboard", loginResponse.getRedirectedUrl());

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
					AuthorityUtils.createAuthorityList("ROLE_LECTURER")));
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

	@Test
	public void roleRulesRejectStudentsFromLecturerAndAdministratorRoutes() throws Exception {
		XmlWebApplicationContext context = new XmlWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.setConfigLocations(new String[] { "classpath:test-repository-context.xml",
				"file:src/main/webapp/WEB-INF/spring-security.xml" });
		context.refresh();
		try {
			FilterChainProxy filter = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
			MockHttpSession studentSession = session("R0004444", "ROLE_USER");

			MockHttpServletResponse lecturerDenied = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/lecturer/dashboard", studentSession), lecturerDenied,
					new MockFilterChain());
			assertEquals(403, lecturerDenied.getStatus());

			MockHttpServletResponse adminDenied = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/admin/student/listall", studentSession), adminDenied,
					new MockFilterChain());
			assertEquals(403, adminDenied.getStatus());

			MockHttpServletResponse studentAllowed = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/student/dashboard", studentSession), studentAllowed,
					new MockFilterChain());
			assertEquals(200, studentAllowed.getStatus());

			MockHttpSession lecturerSession = session("L001", "ROLE_LECTURER");
			MockHttpServletResponse lecturerCrudDenied = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/admin/student/listall", lecturerSession), lecturerCrudDenied,
					new MockFilterChain());
			assertEquals(403, lecturerCrudDenied.getStatus());

			MockHttpSession administratorSession = session("A0000001", "ROLE_ADMIN");
			MockHttpServletResponse administratorAllowed = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/admin/student/listall", administratorSession), administratorAllowed,
					new MockFilterChain());
			assertEquals(200, administratorAllowed.getStatus());

			MockHttpServletResponse anonymousDenied = new MockHttpServletResponse();
			filter.doFilter(request("GET", "/admin/student/listall", null), anonymousDenied,
					new MockFilterChain());
			assertEquals(302, anonymousDenied.getStatus());
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

	private MockHttpSession session(String username, String role) {
		MockHttpSession session = new MockHttpSession();
		SecurityContext security = SecurityContextHolder.createEmptyContext();
		security.setAuthentication(new UsernamePasswordAuthenticationToken(username, "unused",
				AuthorityUtils.createAuthorityList(role)));
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
		return session;
	}
}
