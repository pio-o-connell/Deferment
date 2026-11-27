package com.defermentsystem.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;

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

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private DataSource dataSource;

	@Before
	public void authenticateAssignedLecturer() {
		setAuthentication("L001", "ROLE_LECTURER");
	}

	@After
	public void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

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
		assertNull(realDao.getDeferral(id).getDecisionLecturerId());
		assertNull(realDao.getDeferral(id).getRejectionReason());
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

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void programmeSubmissionCommitsEveryRecordAndDocument() throws IOException {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl", DeferralServiceImpl.class);
		Path directory = temporaryFolder.newFolder().toPath();
		byte[] document = "test document".getBytes(StandardCharsets.UTF_8);
		ArrayList<Integer> ids = service.submitProgrammeDeferral("R0020934", "KCLDC_Y4", document, directory);

		assertEquals(2, ids.size());
		for (Integer id : ids) {
			assertEquals("R0020934", new JdbcTemplate(dataSource).queryForObject(
					"select Student_ID from deferrals where Def_ID=?", String.class, id));
			assertEquals("test document", new String(Files.readAllBytes(directory.resolve(id + ".pdf")), StandardCharsets.UTF_8));
		}
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void documentFailureRollsBackProgrammeAndRemovesOnlyNewFiles() throws IOException {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl", DeferralServiceImpl.class);
		final DeferralDAO realDao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		final Path directory = temporaryFolder.newFolder().toPath();
		final ArrayList<Integer> generatedIds = new ArrayList<Integer>();
		DeferralDAO dao = (DeferralDAO) Proxy.newProxyInstance(DeferralDAO.class.getClassLoader(),
				new Class<?>[] { DeferralDAO.class }, new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						try {
							Object result = method.invoke(realDao, args);
							if ("createProgrammeDeferralGetId".equals(method.getName())) {
								@SuppressWarnings("unchecked")
								ArrayList<Integer> ids = (ArrayList<Integer>) result;
								generatedIds.addAll(ids);
								Files.write(directory.resolve(ids.get(1) + ".pdf"), "existing".getBytes(StandardCharsets.UTF_8));
							}
							return result;
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					}
				});
		setServiceDao(service, dao);
		try {
			service.submitProgrammeDeferral("R0020934", "KCLDC_Y4",
					"document".getBytes(StandardCharsets.UTF_8), directory);
			fail("Expected a file collision");
		} catch (IOException expected) {
			assertEquals(2, generatedIds.size());
		} finally {
			setServiceDao(service, realDao);
		}

		assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from deferrals where Student_ID=?", Integer.class, "R0020934"));
		assertFalse(Files.exists(directory.resolve(generatedIds.get(0) + ".pdf")));
		assertEquals("existing", new String(Files.readAllBytes(directory.resolve(generatedIds.get(1) + ".pdf")),
				StandardCharsets.UTF_8));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void invalidRegistrationDoesNotCreateSubmission() throws IOException {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl", DeferralServiceImpl.class);
		Path directory = temporaryFolder.newFolder().toPath();
		try {
			service.submitModuleDeferral("R0004444", "COMP8016", 24248, new byte[] {1}, directory);
			fail("Expected mismatched module rejection");
		} catch (IllegalArgumentException expected) {
			assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
					"select count(*) from deferrals where Student_ID=?", Integer.class, "R0004444"));
		}
		try {
			service.submitProgrammeDeferral("R0004444", "KCLDC_Y4", new byte[] {1}, directory);
			fail("Expected missing registration rejection");
		} catch (IllegalArgumentException expected) {
			assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject(
					"select count(*) from deferrals where Student_ID=?", Integer.class, "R0004444"));
		}
		assertEquals(0, directory.toFile().list().length);
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void assignedLecturerCanRejectAndOptionalReasonIsStored() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl", DeferralServiceImpl.class);
		DeferralDAO dao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		int id = dao.createDeferralGetId("R0020934", "L002", "KCLDC_Y4", "COMP8038");
		try {
			service.decideDeferral(id, "L001", "Rejected", "reason");
			fail("Expected unauthorised decision rejection");
		} catch (InvalidDeferralStateException expected) {
			assertEquals("False", dao.getDeferral(id).getApproval());
		}
		setAuthentication("L002", "ROLE_LECTURER");
		service.decideDeferral(id, "L002", "Rejected", "  Further evidence needed  ");
		assertEquals("Rejected", dao.getDeferral(id).getApproval());
		assertEquals("L002", dao.getDeferral(id).getDecisionLecturerId());
		assertEquals("Further evidence needed", dao.getDeferral(id).getRejectionReason());

		int secondId = dao.createDeferralGetId("R0004444", "L001", "KCMSD_Y5", "COMP8040");
		setAuthentication("L001", "ROLE_LECTURER");
		service.decideDeferral(secondId, "L001", "Rejected", "");
		assertEquals(null, dao.getDeferral(secondId).getRejectionReason());
		assertTrue(dao.getDeferral(secondId).getApproval().equals("Rejected"));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void nonexistentDeferralDecisionMakesNoChange() {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);

		try {
			service.decideDeferral(999999, "L001", "True");
			fail("Expected missing deferral rejection");
		} catch (InvalidDeferralStateException expected) {
			assertTrue(expected.getMessage().contains("does not exist"));
		}
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void duplicatePendingSubmissionIsRejected() throws IOException {
		DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl",
				DeferralServiceImpl.class);
		Path directory = temporaryFolder.newFolder().toPath();
		byte[] document = "test document".getBytes(StandardCharsets.UTF_8);

		service.submitModuleDeferral("R0004444", "COMP8040", 24248, document, directory);
		try {
			service.submitModuleDeferral("R0004444", "COMP8040", 24248, document, directory);
			fail("Expected duplicate pending submission rejection");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("pending module deferral"));
		}

		assertEquals(Integer.valueOf(1), new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from deferrals where Student_ID=? and Module_ID=?",
				Integer.class, "R0004444", "COMP8040"));
	}

	@Test
	@DatabaseSetup(value = "classpath:databaseEntries.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void concurrentDecisionsCannotBothSucceed() throws Exception {
		final DeferralServiceImpl service = applicationContext.getBean("deferralServiceImpl", DeferralServiceImpl.class);
		DeferralDAO dao = applicationContext.getBean("deferralJdbcDaoSupport", DeferralDAO.class);
		final int id = dao.createDeferralGetId("R0004444", "L001", "KCMSD_Y5", "COMP8040");
		final CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> approval = executor.submit(decisionAttempt(service, id, "True", start));
			Future<Boolean> rejection = executor.submit(decisionAttempt(service, id, "Rejected", start));
			start.countDown();
			boolean approved = approval.get(15, TimeUnit.SECONDS);
			boolean rejected = rejection.get(15, TimeUnit.SECONDS);
			assertTrue(approved != rejected);
			assertEquals(approved ? "True" : "Rejected", dao.getDeferral(id).getApproval());
			assertEquals("L001", dao.getDeferral(id).getDecisionLecturerId());
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
		}
	}

	private Callable<Boolean> decisionAttempt(final DeferralServiceImpl service, final int id,
			final String decision, final CountDownLatch start) {
		return new Callable<Boolean>() {
			public Boolean call() throws Exception {
				start.await();
				setAuthentication("L001", "ROLE_LECTURER");
				try {
					service.decideDeferral(id, "L001", decision);
					return true;
				} catch (InvalidDeferralStateException | DataAccessException expected) {
					return false;
				} finally {
					SecurityContextHolder.clearContext();
				}
			}
		};
	}

	private void setAuthentication(String username, String role) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(username, "unused",
						AuthorityUtils.createAuthorityList(role)));
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
