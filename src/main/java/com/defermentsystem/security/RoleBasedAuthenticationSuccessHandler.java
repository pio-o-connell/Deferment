package com.defermentsystem.security;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		String destination = dashboardFor(authentication.getAuthorities());
		if (destination == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + destination));
	}

	private String dashboardFor(Collection<? extends GrantedAuthority> authorities) {
		boolean lecturer = false;
		boolean student = false;
		for (GrantedAuthority authority : authorities) {
			if ("ROLE_ADMIN".equals(authority.getAuthority())) {
				return "/admin/dashboard";
			}
			lecturer |= "ROLE_LECTURER".equals(authority.getAuthority());
			student |= "ROLE_USER".equals(authority.getAuthority());
		}
		if (lecturer) {
			return "/lecturer/dashboard";
		}
		if (student) {
			return "/student/dashboard";
		}
		return null;
	}
}
