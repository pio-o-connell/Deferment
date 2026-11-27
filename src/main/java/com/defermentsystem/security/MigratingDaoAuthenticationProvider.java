package com.defermentsystem.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

public class MigratingDaoAuthenticationProvider extends DaoAuthenticationProvider {

	private BCryptPasswordEncoder bcryptPasswordEncoder;
	private JdbcTemplate jdbcTemplate;

	public void setBcryptPasswordEncoder(BCryptPasswordEncoder bcryptPasswordEncoder) {
		this.bcryptPasswordEncoder = bcryptPasswordEncoder;
	}

	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	protected void additionalAuthenticationChecks(UserDetails userDetails,
			UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
		Object credentials = authentication.getCredentials();
		if (credentials == null) {
			throw new BadCredentialsException("Invalid username or password.");
		}
		String rawPassword = credentials.toString();
		String storedPassword = userDetails.getPassword();
		if (isBcryptHash(storedPassword)) {
			super.additionalAuthenticationChecks(userDetails, authentication);
			return;
		}
		if (!MessageDigest.isEqual(storedPassword.getBytes(StandardCharsets.UTF_8),
				rawPassword.getBytes(StandardCharsets.UTF_8))) {
			throw new BadCredentialsException("Invalid username or password.");
		}

		String upgradedPassword = bcryptPasswordEncoder.encode(rawPassword);
		int updated = jdbcTemplate.update(
				"update users set password=? where username=? and password=?",
				upgradedPassword, userDetails.getUsername(), storedPassword);
		if (updated != 1) {
			String latestPassword = jdbcTemplate.queryForObject(
					"select password from users where username=?",
					String.class, userDetails.getUsername());
			if (!isBcryptHash(latestPassword)
					|| !bcryptPasswordEncoder.matches(rawPassword, latestPassword)) {
				throw new AuthenticationServiceException("Could not safely upgrade the account password.");
			}
		}
	}

	private boolean isBcryptHash(String value) {
		return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$")
				|| value.startsWith("$2y$"));
	}
}
