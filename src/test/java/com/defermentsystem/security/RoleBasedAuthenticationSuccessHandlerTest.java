package com.defermentsystem.security;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class RoleBasedAuthenticationSuccessHandlerTest {

	@Test
	public void routesEachSupportedRoleToItsDashboard() throws Exception {
		assertDestination("ROLE_USER", "/student/dashboard");
		assertDestination("ROLE_LECTURER", "/lecturer/dashboard");
		assertDestination("ROLE_ADMIN", "/admin/dashboard");
	}

	@Test
	public void administratorDashboardTakesPrecedenceForMultipleRoles() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("/deferment");
		MockHttpServletResponse response = new MockHttpServletResponse();
		new RoleBasedAuthenticationSuccessHandler().onAuthenticationSuccess(request, response,
				new UsernamePasswordAuthenticationToken("admin", "unused",
						AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMIN")));

		assertEquals("/deferment/admin/dashboard", response.getRedirectedUrl());
	}

	private void assertDestination(String role, String destination) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("/deferment");
		MockHttpServletResponse response = new MockHttpServletResponse();
		new RoleBasedAuthenticationSuccessHandler().onAuthenticationSuccess(request, response,
				new UsernamePasswordAuthenticationToken("account", "unused",
						AuthorityUtils.createAuthorityList(role)));

		assertEquals("/deferment" + destination, response.getRedirectedUrl());
	}
}
