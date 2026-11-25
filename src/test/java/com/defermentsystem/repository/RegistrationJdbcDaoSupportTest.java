package com.defermentsystem.repository;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.defermentsystem.domain.Registration;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;



@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "classpath:test-repository-context.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
		DbUnitTestExecutionListener.class })

public class RegistrationJdbcDaoSupportTest {
	
	@Autowired
	ApplicationContext autoWireContext;
	@Autowired
	DataSource dataSource;
	RegistrationJdbcDaoSupport registrationJdbcDaoSupport;

	@Test
	public void testDatabaseIsInMemoryAndHasApplicationTables() throws Exception {
		Set<String> tables = new HashSet<String>();
		try (Connection connection = dataSource.getConnection()) {
			assertTrue("Repository tests must use an in-memory HSQLDB",
					connection.getMetaData().getURL().startsWith("jdbc:hsqldb:mem:"));
			try (ResultSet result = connection.getMetaData().getTables(null, "PUBLIC", null,
					new String[] { "TABLE" })) {
				while (result.next()) {
					tables.add(result.getString("TABLE_NAME"));
				}
			}
		}
		assertTrue("Missing test tables: " + tables, tables.containsAll(Arrays.asList(
				"DEFERRALS", "LECTURER", "MODULES", "PROGRAMME", "REGISTRATION",
				"SEMESTER", "STUDENT", "USERS", "USER_ROLES")));
	}


	//Test for retrieving list of Registrations
	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void testListRegistrations(){
		registrationJdbcDaoSupport = (RegistrationJdbcDaoSupport) autoWireContext.getBean("registrationJdbcDaoSupport");
		List<Registration> registrations=registrationJdbcDaoSupport.listRegistrations();
		assertEquals(registrations.size(), 3);
		boolean found = false;
		for (Registration registration : registrations) {
			if ("R0020934".equals(registration.getStudentId())
					&& "KCLDC_Y4".equals(registration.getProgrammeId())) {
				found = true;
			}
		}
		assertTrue("Expected a registration for R0020934 in KCLDC_Y4", found);

	}
}

