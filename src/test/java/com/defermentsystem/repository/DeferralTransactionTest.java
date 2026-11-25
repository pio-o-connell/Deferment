package com.defermentsystem.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.sql.DataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;

import com.defermentsystem.exceptions.InvalidDeferralStateException;
import com.defermentsystem.service.DeferralServiceImpl;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({ "classpath:test-repository-context.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
		DbUnitTestExecutionListener.class })
public class DeferralTransactionTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private DataSource dataSource;

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void successfulSubmissionAndApprovalCommit() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);

		int id = service.createModuleDeferralGetId("R0004444", "COMP8040", 24248);

		assertEquals("R0004444", new JdbcTemplate(dataSource).queryForObject(
				"select Student_ID from deferrals where Def_ID=?", String.class, id));

		service.decideDeferral(id, "L001", "True");
		assertEquals("True", new JdbcTemplate(dataSource).queryForObject(
				"select Approved from deferrals where Def_ID=?", String.class, id));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void laterSubmissionFailureRollsBackEarlierInsert() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);
		DeferralDAO realDao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		DeferralDAO failingDao = failingDao(realDao, 1);
		setServiceDao(service, failingDao);

		try {
			service.createProgrammeDeferral("R0020934", "KCLDC_Y4");
			fail("Expected injected submission failure");
		} catch (IllegalStateException expected) {
			// Expected controlled failure.
		} finally {
			setServiceDao(service, realDao);
		}

		assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from deferrals where Student_ID=?", Integer.class, "R0020934"));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void statusUpdateFailureRollsBackDecision() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);
		DeferralDAO realDao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		int id = realDao.createDeferralGetId("R0004444", "L001", "KCMSD_Y5", "COMP8040");
		DeferralDAO failingDao = failingStatusUpdateDao(realDao);
		setServiceDao(service, failingDao);

		try {
			service.decideDeferral(id, "L001", "True");
			fail("Expected injected status failure");
		} catch (IllegalStateException expected) {
			// The service proxy must roll back the real status write.
		} finally {
			setServiceDao(service, realDao);
		}

		assertEquals("False", new JdbcTemplate(dataSource).queryForObject(
				"select Approved from deferrals where Def_ID=?", String.class, id));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void duplicateDecisionAndWrongLecturerAreRejected() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);
		DeferralDAO realDao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		int id = realDao.createDeferralGetId("R0004444", "L001", "KCMSD_Y5", "COMP8040");

		service.decideDeferral(id, "L001", "True");

		try {
			service.decideDeferral(id, "L001", "Rejected");
			fail("Expected duplicate decision rejection");
		} catch (InvalidDeferralStateException expected) {
			// Expected.
		}

		try {
			service.decideDeferral(id, "L002", "Rejected");
			fail("Expected wrong lecturer rejection");
		} catch (InvalidDeferralStateException expected) {
			// Expected.
		}

		assertEquals("True", new JdbcTemplate(dataSource).queryForObject(
				"select Approved from deferrals where Def_ID=?", String.class, id));
	}

	private DeferralDAO failingDao(final DeferralDAO delegate, final int failOnCall) {
		return (DeferralDAO) Proxy.newProxyInstance(
				DeferralDAO.class.getClassLoader(),
				new Class<?>[] { DeferralDAO.class },
				new InvocationHandler() {
					private int callCount;

					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						if ("createProgrammeDeferral".equals(method.getName())) {
							callCount++;
							if (callCount == failOnCall) {
								method.invoke(delegate, args);
								throw new IllegalStateException("Injected module association failure");
							}
						}
						return method.invoke(delegate, args);
					}
				});
	}

	private DeferralDAO failingStatusUpdateDao(final DeferralDAO delegate) {
		return (DeferralDAO) Proxy.newProxyInstance(
				DeferralDAO.class.getClassLoader(),
				new Class<?>[] { DeferralDAO.class },
				new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						if ("updateDeferralStatusIfPending".equals(method.getName())) {
							method.invoke(delegate, args);
							throw new IllegalStateException("Injected status update failure");
						}
						return method.invoke(delegate, args);
					}
				});
	}

	private void setServiceDao(DeferralServiceImpl service, DeferralDAO dao) {
		try {
			Object target = ((Advised) service).getTargetSource().getTarget();
			ReflectionTestUtils.setField(target, "deferralDAO", dao);
		} catch (Exception e) {
			throw new IllegalStateException("Could not replace the transactional service DAO", e);
		}
	}
}
