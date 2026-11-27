package com.defermentsystem.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.defermentsystem.domain.Deferral;
import com.defermentsystem.domain.Mail;
import com.defermentsystem.domain.Module;
import com.defermentsystem.domain.Student;
import com.defermentsystem.service.DeferralService;
import com.defermentsystem.service.ModuleService;
import com.defermentsystem.service.StudentService;

public class DeferralControllerTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void failedSubmissionsReturnErrorWithoutSendingMail() throws IOException {
		final int[] calls = new int[1];
		DeferralService deferrals = proxy(DeferralService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				if (method.getName().startsWith("submit")) {
					calls[0]++;
					throw new IOException("Controlled document failure");
				}
				throw new AssertionError("Unexpected call: " + method.getName());
			}
		});
		final Student student = new Student("R0004444", "Test", "Student", "test@example.test");
		StudentService students = proxy(StudentService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return student;
			}
		});
		ModuleService modules = proxy(ModuleService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return new Module("COMP8040", 24248, "Module", "L001", "S1");
			}
		});
		RecordingMail mail = new RecordingMail();
		DeferralController controller = new DeferralController();
		ReflectionTestUtils.setField(controller, "deferralService", deferrals);
		ReflectionTestUtils.setField(controller, "studentService", students);
		ReflectionTestUtils.setField(controller, "moduleService", modules);
		ReflectionTestUtils.setField(controller, "mail", mail);
		final String root = temporaryFolder.newFolder().getAbsolutePath();
		ReflectionTestUtils.setField(controller, "servletContext", new MockServletContext() {
			@Override
			public String getRealPath(String path) {
				return root;
			}
		});
		MockMultipartFile document = pdfDocument();
		Deferral module = new Deferral();
		module.setStudentId("R0004444");
		module.setModuleId("Module");
		module.setProgrammeId("24248");
		ModelMap moduleModel = new ModelMap();
		assertEquals("displayError", controller.displayModuleDeferralPage2(module, document,
				new BeanPropertyBindingResult(module, "deferral"), principal("R0004444"), moduleModel));
		assertTrue(moduleModel.containsKey("message"));

		Deferral programme = new Deferral();
		programme.setStudentId("R0004444");
		programme.setProgrammeId("KCMSD_Y5");
		ModelMap programmeModel = new ModelMap();
		assertEquals("displayError", controller.displayProgrammeDeferralPage2(programme, document,
				new BeanPropertyBindingResult(programme, "deferral"), principal("R0004444"), programmeModel));
		assertTrue(programmeModel.containsKey("message"));
		assertEquals(2, calls[0]);
		assertFalse(mail.sent);
	}

	@Test
	public void decisionRequiresAuthenticatedUserAndPassesReasonToService() {
		final Object[][] decision = new Object[1][];
		DeferralService service = proxy(DeferralService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ("decideDeferral".equals(method.getName())) {
					decision[0] = args;
					return null;
				}
				if ("getDeferral".equals(method.getName())) {
					return new Deferral(1, "R0004444", "L002", "KCLDC_Y4", "COMP8038");
				}
				if ("listDeferrals".equals(method.getName())) {
					return Collections.emptyList();
				}
				throw new AssertionError("Unexpected call: " + method.getName());
			}
		});
		DeferralController controller = new DeferralController();
		ReflectionTestUtils.setField(controller, "deferralService", service);
		for (Principal principal : new Principal[] { null }) {
			try {
				controller.decideDeferral(1, "Rejected", "reason", principal, new ModelMap());
				fail("Expected access denial");
			} catch (AccessDeniedException expected) {
				assertEquals(null, decision[0]);
			}
		}
		assertEquals("displayDeferrals", controller.decideDeferral(1, "Rejected", "reason",
				principal("L001"), new ModelMap()));
		assertEquals("L001", decision[0][1]);
		assertEquals("Rejected", decision[0][2]);
		assertEquals("reason", decision[0][3]);
	}

	@Test
	public void notificationFailureReportsThatTheSubmissionWasSaved() throws IOException {
		final int[] submissions = new int[1];
		DeferralService deferrals = proxy(DeferralService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ("submitProgrammeDeferral".equals(method.getName())) {
					submissions[0]++;
					return new java.util.ArrayList<Integer>(Collections.singletonList(1));
				}
				if ("listDeferralsByStudent".equals(method.getName())) {
					return Collections.singletonList(new Deferral());
				}
				throw new AssertionError("Unexpected call: " + method.getName());
			}
		});
		StudentService students = proxy(StudentService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return new Student("R0004444", "Test", "Student", "test@example.test");
			}
		});
		final String root = temporaryFolder.newFolder().getAbsolutePath();
		DeferralController controller = new DeferralController();
		ReflectionTestUtils.setField(controller, "deferralService", deferrals);
		ReflectionTestUtils.setField(controller, "studentService", students);
		ReflectionTestUtils.setField(controller, "mail", new Mail() {
			@Override
			public void sendProgrammeDeferralMail(String name, String id, String programme) {
				throw new MailSendException("Controlled SMTP failure");
			}
		});
		ReflectionTestUtils.setField(controller, "servletContext", new MockServletContext() {
			@Override
			public String getRealPath(String path) {
				return root;
			}
		});
		Deferral deferral = new Deferral();
		deferral.setStudentId("R0004444");
		deferral.setProgrammeId("KCMSD_Y5");
		ModelMap model = new ModelMap();
		MockMultipartFile document = pdfDocument();
		assertEquals("displayDeferrals", controller.displayProgrammeDeferralPage2(deferral, document,
				new BeanPropertyBindingResult(deferral, "deferral"), principal("R0004444"), model));
		assertEquals(1, submissions[0]);
		assertTrue(model.get("message").toString().contains("saved"));
		assertTrue(model.get("message").toString().contains("notification"));
	}

	@Test
	public void rejectsFakePdfContentBeforeCreatingSubmission() throws IOException {
		final int[] submissions = new int[1];
		DeferralService deferrals = proxy(DeferralService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ("submitProgrammeDeferral".equals(method.getName())) {
					submissions[0]++;
				}
				throw new AssertionError("Submission should be rejected before persistence.");
			}
		});
		StudentService students = proxy(StudentService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return new Student("R0004444", "Test", "Student", "test@example.test");
			}
		});
		DeferralController controller = new DeferralController();
		ReflectionTestUtils.setField(controller, "deferralService", deferrals);
		ReflectionTestUtils.setField(controller, "studentService", students);
		ReflectionTestUtils.setField(controller, "mail", new RecordingMail());
		ReflectionTestUtils.setField(controller, "servletContext", new MockServletContext() {
			@Override
			public String getRealPath(String path) {
				return temporaryFolder.getRoot().getAbsolutePath();
			}
		});
		Deferral deferral = new Deferral();
		deferral.setStudentId("R0004444");
		deferral.setProgrammeId("KCMSD_Y5");
		ModelMap model = new ModelMap();
		MockMultipartFile spoofedPdf = new MockMultipartFile("file", "form.pdf",
				"application/pdf", "not a PDF".getBytes(StandardCharsets.US_ASCII));

		assertEquals("displayError", controller.displayProgrammeDeferralPage2(deferral, spoofedPdf,
				new BeanPropertyBindingResult(deferral, "deferral"), principal("R0004444"), model));
		assertEquals(0, submissions[0]);
	}

	@Test
	public void mvcAcceptsMultipartSubmissionAndPostDecisionButNotLegacyGetDecisions() throws Exception {
		final int[] submissions = new int[1];
		final int[] decisions = new int[1];
		DeferralService deferrals = proxy(DeferralService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				if ("submitModuleDeferral".equals(method.getName())) {
					submissions[0]++;
					return 1;
				}
				if ("decideDeferral".equals(method.getName())) {
					decisions[0]++;
					return null;
				}
				if ("getDeferral".equals(method.getName())) {
					return new Deferral(1, "R0004444", "L001", "KCMSD_Y5", "COMP8040");
				}
				if ("listDeferrals".equals(method.getName()) || "listDeferralsByStudent".equals(method.getName())) {
					return Collections.emptyList();
				}
				throw new AssertionError("Unexpected call: " + method.getName());
			}
		});
		StudentService students = proxy(StudentService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return new Student("R0004444", "Test", "Student", "test@example.test");
			}
		});
		ModuleService modules = proxy(ModuleService.class, new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) {
				return new Module("COMP8040", 24248, "Module", "L001", "S1");
			}
		});
		final String root = temporaryFolder.newFolder().getAbsolutePath();
		DeferralController controller = new DeferralController();
		ReflectionTestUtils.setField(controller, "deferralService", deferrals);
		ReflectionTestUtils.setField(controller, "studentService", students);
		ReflectionTestUtils.setField(controller, "moduleService", modules);
		ReflectionTestUtils.setField(controller, "mail", new RecordingMail());
		ReflectionTestUtils.setField(controller, "servletContext", new MockServletContext() {
			@Override
			public String getRealPath(String path) {
				return root;
			}
		});
		InternalResourceViewResolver resolver = new InternalResourceViewResolver();
		resolver.setPrefix("/WEB-INF/jsp/");
		resolver.setSuffix(".jsp");
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
		assertEquals(200, mvc.perform(fileUpload("/admin/deferral/addModuleDeferralPage2")
				.file(pdfDocument())
				.param("studentId", "R0004444").param("moduleId", "Module").param("programmeId", "24248")
				.principal(principal("R0004444")))
				.andReturn().getResponse().getStatus());
		assertEquals(1, submissions[0]);
		assertEquals(200, mvc.perform(post("/admin/deferral/modify/id/1/decision")
				.principal(principal("L001")).param("approval", "Rejected").param("rejectionReason", "reason"))
				.andReturn().getResponse().getStatus());
		assertEquals(1, decisions[0]);
		assertEquals(404, mvc.perform(get("/admin/deferral/modify/id/1/approval/True"))
				.andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get("/admin/deferral/listall/COMP8040/R0004444"))
				.andReturn().getResponse().getStatus());
		assertEquals(1, decisions[0]);
	}

	private Principal principal(final String username) {
		return new Principal() {
			public String getName() {
				return username;
			}
		};
	}

	private MockMultipartFile pdfDocument() {
		return new MockMultipartFile("file", "form.pdf", "application/pdf",
				"%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
	}

	private <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
	}

	private static class RecordingMail extends Mail {
		private boolean sent;

		@Override
		public void sendModuleDeferralMail(String name, String id, String module, int crn) {
			sent = true;
		}

		@Override
		public void sendProgrammeDeferralMail(String name, String id, String programme) {
			sent = true;
		}
	}
}
