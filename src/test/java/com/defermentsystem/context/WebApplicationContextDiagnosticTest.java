package com.defermentsystem.context;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Test;
import org.springframework.aop.TargetSource;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WebApplicationContextDiagnosticTest {

	private static final String[] ROOT_CONFIG_LOCATIONS = {
			"/WEB-INF/root-context.xml",
			"/WEB-INF/spring-security.xml" };

	private static final String[] CONTROLLER_BEANS = {
			"deferralController", "homeController", "lecturerController",
			"loginController", "moduleController", "programmeController",
			"studentController" };

	private static final String[] SERVICE_BEANS = {
			"deferralServiceImpl", "lecturerServiceImpl", "moduleServiceImpl",
			"programmeServiceImpl", "registrationServiceImpl",
			"semesterServiceImpl", "studentServiceImpl" };

	private static final String[] REPOSITORY_BEANS = {
			"deferralJdbcDaoSupport", "lecturerJdbcDaoSupport",
			"moduleJdbcDaoSupport", "programmeJdbcDaoSupport",
			"registrationJdbcDaoSupport", "semesterJdbcDaoSupport",
			"studentJdbcDaoSupport" };

	private static final String[] ROOT_INFRASTRUCTURE_BEANS = {
			"dataSource", "transactionManager", "txTemplate", "mailSender", "mail" };

	private static final String[] CHILD_MVC_INFRASTRUCTURE_BEANS = {
			"validator", "tilesConfigurer", "tilesViewResolver", "multipartResolver" };

	@Test
	public void reportActualWebApplicationContexts() {
		File webRoot = new File("src/main/webapp");
		MockServletContext servletContext =
				new MockServletContext(webRoot.getAbsolutePath(),
						new FileSystemResourceLoader());
		XmlWebApplicationContext rootContext = new XmlWebApplicationContext();
		TrackingDispatcherServlet dispatcherServlet = new TrackingDispatcherServlet();
		ConfigurableWebApplicationContext childContext = null;
		boolean rootInitialized = false;
		boolean dispatcherInitialized = false;
		String initializationStage = "root context";

		try {
			rootContext.setServletContext(servletContext);
			rootContext.setConfigLocations(ROOT_CONFIG_LOCATIONS);
			rootContext.refresh();
			rootInitialized = true;
			System.out.println("ROOT context started: " + rootContext.getId());

			servletContext.setAttribute(
					WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
					rootContext);

			initializationStage = "mvc-dispatcher child context";
			dispatcherServlet.init(new MockServletConfig(servletContext, "mvc-dispatcher"));
			dispatcherInitialized = true;
			childContext = dispatcherServlet.getCreatedContext();

			assertNotNull("DispatcherServlet child context was not created", childContext);
			assertNotSameContext(rootContext, childContext);
			assertSame("The DispatcherServlet context must inherit the root context",
					rootContext, childContext.getParent());
			System.out.println("CHILD context started: " + childContext.getId());
			System.out.println("Child parent is Root: PASS");

			initializationStage = "bean registration and context separation";
			reportContextSeparation(rootContext, childContext);
			assertLocallyRegistered(rootContext, "ROOT", SERVICE_BEANS);
			assertLocallyRegistered(rootContext, "ROOT", REPOSITORY_BEANS);
			assertLocallyRegistered(rootContext, "ROOT", ROOT_INFRASTRUCTURE_BEANS);
			assertLocallyRegistered(childContext, "CHILD", CONTROLLER_BEANS);
			assertLocallyRegistered(childContext, "CHILD", CHILD_MVC_INFRASTRUCTURE_BEANS);
			assertNotLocallyRegistered(rootContext, "ROOT", CONTROLLER_BEANS);
			assertNotLocallyRegistered(childContext, "CHILD", SERVICE_BEANS);
			assertNotLocallyRegistered(childContext, "CHILD", REPOSITORY_BEANS);
			assertNotLocallyRegistered(childContext, "CHILD", ROOT_INFRASTRUCTURE_BEANS);
			assertNotLocallyRegistered(rootContext, "ROOT", CHILD_MVC_INFRASTRUCTURE_BEANS);
			assertTrue("Mail must be locally registered in Root",
					rootContext.getBeanFactory().containsBeanDefinition("mail"));
			assertFalse("Mail must not be locally registered in Child",
					childContext.getBeanFactory().containsBeanDefinition("mail"));
			assertSame("Child must inherit the Root Mail bean",
					rootContext.getBean("mail"), childContext.getBean("mail"));

			for (String beanName : ROOT_INFRASTRUCTURE_BEANS) {
				assertSame("Child must inherit Root bean " + beanName,
						rootContext.getBean(beanName), childContext.getBean(beanName));
			}
			for (String beanName : SERVICE_BEANS) {
				assertSame("Child must inherit Root service " + beanName,
						rootContext.getBean(beanName), childContext.getBean(beanName));
			}
			for (String beanName : REPOSITORY_BEANS) {
				assertSame("Child must inherit Root repository " + beanName,
						rootContext.getBean(beanName), childContext.getBean(beanName));
			}
			System.out.println("Root infrastructure, services and repositories inherited by Child: PASS");

			initializationStage = "bean inventory and dependency injection";
			reportBeanInventory("ROOT", rootContext, childContext);
			reportBeanInventory("CHILD", childContext, rootContext);
			reportDuplicateComponents(rootContext, childContext);
			reportInjections("SERVICE-TO-REPOSITORY INJECTION",
					SERVICE_BEANS, rootContext, childContext);
			reportInjections("CONTROLLER DEPENDENCY INJECTION",
					CONTROLLER_BEANS, rootContext, childContext);
			System.out.println("Required autowired fields: PASS");
			initializationStage = "repository datasource and transaction advice";
			reportRepositoryTransactionProxies(rootContext, childContext);
			initializationStage = "MVC handler mappings";
			reportHandlerMappings(rootContext, childContext);
			System.out.println("MVC handler mappings: PASS");
			System.out.println("RUNTIME CONTEXT DIAGNOSTIC: PASS");
		} catch (Exception exception) {
			System.err.println("FAILED while initializing or inspecting "
					+ initializationStage + ": " + exception);
			exception.printStackTrace(System.err);
			throw new AssertionError("Diagnostic failed during " + initializationStage,
					exception);
		} finally {
			ConfigurableWebApplicationContext createdChildContext =
					dispatcherServlet.getCreatedContext();
			try {
				if (dispatcherInitialized || createdChildContext != null) {
					dispatcherServlet.destroy();
				}
			} finally {
				try {
					if (createdChildContext != null && createdChildContext.isActive()) {
						createdChildContext.close();
					}
				} finally {
					try {
						if (rootInitialized && rootContext.isActive()) {
							rootContext.close();
						}
					} finally {
						servletContext.removeAttribute(
								WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
					}
				}
			}
		}
	}

	private void reportContextSeparation(ConfigurableApplicationContext root,
			ConfigurableApplicationContext child) {
		System.out.println("\n========== CONTEXT SEPARATION ==========");
		Set<String> rootServices = localPackageBeans(root, "com.defermentsystem.service.");
		Set<String> rootRepositories = localPackageBeans(root, "com.defermentsystem.repository.");
		rootRepositories.remove("dataSource");
		Set<String> childControllers = localPackageBeans(child, "com.defermentsystem.controllers.");
		Set<String> rootControllers = localPackageBeans(root, "com.defermentsystem.controllers.");
		Set<String> childServices = localPackageBeans(child, "com.defermentsystem.service.");
		Set<String> childRepositories = localPackageBeans(child, "com.defermentsystem.repository.");
		reportCategory("Root services", rootServices, new TreeSet<String>(Arrays.asList(SERVICE_BEANS)));
		reportCategory("Root repositories", rootRepositories, new TreeSet<String>(Arrays.asList(REPOSITORY_BEANS)));
		reportCategory("Child controllers", childControllers, new TreeSet<String>(Arrays.asList(CONTROLLER_BEANS)));
		reportCategory("Controllers in Root", rootControllers, Collections.<String>emptySet());
		reportCategory("Services in Child", childServices, Collections.<String>emptySet());
		reportCategory("Repositories in Child", childRepositories, Collections.<String>emptySet());
		assertEquals("Root service registrations", new TreeSet<String>(Arrays.asList(SERVICE_BEANS)), rootServices);
		assertEquals("Root repository registrations", new TreeSet<String>(Arrays.asList(REPOSITORY_BEANS)), rootRepositories);
		assertEquals("Child controller registrations", new TreeSet<String>(Arrays.asList(CONTROLLER_BEANS)), childControllers);
		assertTrue("Controllers must not be local to Root: " + rootControllers, rootControllers.isEmpty());
		assertTrue("Services must not be local to Child: " + childServices, childServices.isEmpty());
		assertTrue("Repositories must not be local to Child: " + childRepositories, childRepositories.isEmpty());
	}

	private Set<String> localPackageBeans(ConfigurableApplicationContext context, String prefix) {
		Set<String> names = new TreeSet<String>();
		for (String name : context.getBeanFactory().getBeanDefinitionNames()) {
			Class<?> type = AopUtils.getTargetClass(getBean(context, name, context.getId()));
			if (type.getName().startsWith(prefix)) {
				names.add(name);
			}
		}
		return names;
	}

	private void reportCategory(String label, Set<String> actual, Set<String> expected) {
		System.out.printf("%s: %d / %d %s | actual=%s%n",
				label, actual.size(), expected.size(), actual.equals(expected) ? "PASS" : "FAIL", actual);
	}

	private void assertNotSameContext(WebApplicationContext rootContext,
			WebApplicationContext childContext) {
		if (rootContext == childContext) {
			throw new AssertionError("Root and DispatcherServlet contexts are the same instance");
		}
	}

	private void assertLocallyRegistered(
			ConfigurableApplicationContext context,
			String contextName,
			String[] beanNames) {
		for (String beanName : beanNames) {
			assertTrue(contextName + " must locally register " + beanName,
					context.getBeanFactory().containsBeanDefinition(beanName));
		}
	}

	private void assertNotLocallyRegistered(
			ConfigurableApplicationContext context,
			String contextName,
			String[] beanNames) {
		for (String beanName : beanNames) {
			assertFalse(contextName + " must not locally register " + beanName,
					context.getBeanFactory().containsBeanDefinition(beanName));
		}
	}

	private void reportBeanInventory(String contextName,
			ConfigurableApplicationContext context,
			ConfigurableApplicationContext otherContext) {
		System.out.println();
		System.out.println("========== " + contextName + " CONTEXT BEANS ==========");
		System.out.println("Local bean definitions only; inherited beans are excluded.");

		String[] beanNames = context.getBeanFactory().getBeanDefinitionNames();
		System.out.println("Local bean definition count: " + beanNames.length);
		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			Object bean = getBean(context, beanName, contextName);
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			boolean duplicate = otherContext.getBeanFactory()
					.containsBeanDefinition(beanName);

			System.out.printf("[%s] %s | target=%s | AOP proxy=%s | also local in other=%s%n",
					contextName, beanName, targetClass.getName(),
					AopUtils.isAopProxy(bean), duplicate);
		}
	}

	private void reportDuplicateComponents(
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		System.out.println();
		System.out.println("========== DUPLICATE COMPONENT REGISTRATIONS ==========");

		Set<String> rootNames = localBeanNames(rootContext);
		Set<String> childNames = localBeanNames(childContext);
		rootNames.retainAll(childNames);

		int duplicateCount = 0;
		for (String beanName : rootNames) {
			Object rootBean = getBean(rootContext, beanName, "ROOT");
			Object childBean = getBean(childContext, beanName, "CHILD");
			Class<?> rootClass = AopUtils.getTargetClass(rootBean);
			Class<?> childClass = AopUtils.getTargetClass(childBean);
			if (rootClass.getName().startsWith("com.defermentsystem.")
					|| childClass.getName().startsWith("com.defermentsystem.")) {
				System.out.printf("%s | ROOT=%s | CHILD=%s%n", beanName,
						rootClass.getName(), childClass.getName());
				duplicateCount++;
			}
		}

		Map<String, Set<String>> rootImplementations =
				localApplicationImplementations(rootContext, "ROOT");
		Map<String, Set<String>> childImplementations =
				localApplicationImplementations(childContext, "CHILD");
		Set<String> duplicateClasses = new TreeSet<String>(rootImplementations.keySet());
		duplicateClasses.retainAll(childImplementations.keySet());

		for (String className : duplicateClasses) {
			System.out.printf("Same implementation class in both contexts: %s"
							+ " | ROOT beans=%s | CHILD beans=%s%n",
					className, rootImplementations.get(className),
					childImplementations.get(className));
		}
		System.out.println("Application component duplicate bean names: " + duplicateCount);
		System.out.println("Application implementation classes registered in both: "
				+ duplicateClasses.size());
		assertTrue("Application implementation classes must not be registered in both contexts: "
				+ duplicateClasses, duplicateClasses.isEmpty());
	}

	private Map<String, Set<String>> localApplicationImplementations(
			ConfigurableApplicationContext context,
			String contextName) {
		Map<String, Set<String>> implementations =
				new TreeMap<String, Set<String>>();
		for (String beanName : context.getBeanFactory().getBeanDefinitionNames()) {
			Object bean = getBean(context, beanName, contextName);
			String className = AopUtils.getTargetClass(bean).getName();
			if (className.startsWith("com.defermentsystem.")) {
				Set<String> beanNames = implementations.get(className);
				if (beanNames == null) {
					beanNames = new TreeSet<String>();
					implementations.put(className, beanNames);
				}
				beanNames.add(beanName);
			}
		}
		return implementations;
	}

	private void reportInjections(String section, String[] beanNames,
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		System.out.println();
		System.out.println("========== " + section + " ==========");
		reportInjectionsForContext("ROOT", beanNames, rootContext,
				rootContext, childContext);
		reportInjectionsForContext("CHILD", beanNames, childContext,
				rootContext, childContext);
	}

	private void reportInjectionsForContext(String contextName, String[] beanNames,
			ConfigurableApplicationContext context,
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		for (String beanName : beanNames) {
			if (!context.getBeanFactory().containsBeanDefinition(beanName)) {
				System.out.printf("[%s] %s | not locally registered%n",
						contextName, beanName);
				continue;
			}

			Object bean = getBean(context, beanName, contextName);
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			Object target = unwrapProxy(bean, contextName, beanName);
			List<Field> autowiredFields = findDependencyFields(target.getClass());

			System.out.printf("[%s] %s | implementation=%s | AOP proxy=%s%n",
					contextName, beanName, targetClass.getName(),
					AopUtils.isAopProxy(bean));
			if (autowiredFields.isEmpty()) {
				System.out.println("  (no dependency fields)");
			}

			for (Field field : autowiredFields) {
				try {
					Object dependency = ReflectionTestUtils.getField(target, field.getName());
					if (dependency == null) {
						System.out.printf("  %s : %s | injected=null%n",
								field.getName(), field.getType().getName());
						Autowired annotation = field.getAnnotation(Autowired.class);
						assertTrue(contextName + "." + beanName + "." + field.getName()
								+ " is a required dependency but was not injected",
								annotation != null && !annotation.required());
						continue;
					}

					Class<?> dependencyTargetClass = AopUtils.getTargetClass(dependency);
					if (isApplicationDependency(field.getType())) {
						String owner = findOwningContext(dependency, rootContext, childContext);
						assertTrue(contextName + "." + beanName + "." + field.getName()
								+ " must reference a Root bean; actual owner=" + owner,
								owner.startsWith("ROOT:"));
					}
					System.out.printf("  %s : %s | actual=%s | AOP proxy=%s"
									+ " | target=%s | owner=%s%n",
							field.getName(), field.getType().getName(),
							dependency.getClass().getName(),
							AopUtils.isAopProxy(dependency),
							dependencyTargetClass.getName(),
							findOwningContext(dependency, rootContext, childContext));
				} catch (RuntimeException exception) {
					throw new IllegalStateException("Could not inspect injected field "
							+ contextName + "." + beanName + "." + field.getName(),
							exception);
				}
			}
		}
	}

	private void reportRepositoryTransactionProxies(
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		System.out.println();
		System.out.println("========== REPOSITORY TRANSACTION PROXIES ==========");
		reportRepositoryTransactionProxiesForContext("ROOT", REPOSITORY_BEANS,
				rootContext);
		reportRepositoryTransactionProxiesForContext("CHILD", REPOSITORY_BEANS,
				childContext);
	}

	private void reportRepositoryTransactionProxiesForContext(String contextName,
			String[] repositoryBeanNames, ConfigurableApplicationContext context) {
		for (String beanName : repositoryBeanNames) {
			if (!context.getBeanFactory().containsBeanDefinition(beanName)) {
				System.out.printf("[%s] %s | not locally registered"
								+ " | transactionManager local=%s | available=%s%n",
						contextName, beanName,
						context.containsLocalBean("transactionManager"),
						context.containsBean("transactionManager"));
				continue;
			}

			Object repository = getBean(context, beanName, contextName);
			Object target = unwrapProxy(repository, contextName, beanName);
			assertTrue(contextName + "." + beanName + " must extend JdbcDaoSupport",
					target instanceof JdbcDaoSupport);
			assertSame(contextName + "." + beanName + " must use the Root dataSource",
					context.getBean("dataSource"), ((JdbcDaoSupport) target).getDataSource());
			AnnotationTransactionAttributeSource annotations = new AnnotationTransactionAttributeSource();
			boolean transactional = false;
			for (Method method : AopUtils.getTargetClass(repository).getMethods()) {
				if (annotations.getTransactionAttribute(method, AopUtils.getTargetClass(repository)) != null) {
					transactional = true;
					assertTrue(beanName + "." + method.getName() + " requires a transaction proxy",
							repository instanceof Advised);
					boolean advised = false;
					for (Advisor advisor : ((Advised) repository).getAdvisors()) {
						if (advisor.getAdvice() instanceof TransactionInterceptor
								&& advisor instanceof PointcutAdvisor
								&& ((PointcutAdvisor) advisor).getPointcut().getMethodMatcher()
										.matches(method, AopUtils.getTargetClass(repository))) {
							TransactionInterceptor interceptor = (TransactionInterceptor) advisor.getAdvice();
							Object manager = ReflectionTestUtils.invokeMethod(interceptor,
									"determineTransactionManager",
									annotations.getTransactionAttribute(method, AopUtils.getTargetClass(repository)));
							assertSame(beanName + " must use the Root transactionManager",
									context.getBean("transactionManager"), manager);
							advised = true;
						}
					}
					assertTrue(beanName + "." + method.getName() + " has no transaction advice", advised);
				}
			}
			if (!transactional) {
				assertEquals(beanName + " must have transaction advice for its transactional methods",
						"semesterJdbcDaoSupport", beanName);
				System.out.printf("WARNING: %s has no @Transactional methods; no transaction proxy is required.%n",
						beanName);
			}
			System.out.printf("[%s] %s | target=%s | AOP proxy=%s"
							+ " | transactionManager local=%s | available=%s%n",
					contextName, beanName,
					AopUtils.getTargetClass(repository).getName(),
					AopUtils.isAopProxy(repository),
					context.containsLocalBean("transactionManager"),
					context.containsBean("transactionManager"));
			System.out.printf("[%s] %s datasource and applicable transaction advice PASS%n",
					contextName, beanName);
		}
	}

	private void reportHandlerMappings(
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		System.out.println();
		System.out.println("========== SPRING MVC HANDLER MAPPINGS ==========");

		String[] rootMappingBeanNames = rootContext.getBeanFactory()
				.getBeanNamesForType(RequestMappingHandlerMapping.class, true, false);
		String[] childMappingBeanNames = childContext.getBeanFactory()
				.getBeanNamesForType(RequestMappingHandlerMapping.class, true, false);
		assertEquals("Root context must not register MVC handler mappings",
				0, rootMappingBeanNames.length);
		assertTrue("Child context must register MVC handler mappings",
				childMappingBeanNames.length > 0);

		Set<String> registeredControllers = new TreeSet<String>();
		for (String mappingBeanName : childMappingBeanNames) {
			RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping)
					getBean(childContext, mappingBeanName, "CHILD");
			for (HandlerMethod handlerMethod : mapping.getHandlerMethods().values()) {
				Object handlerBean = handlerMethod.getBean();
				String handlerBeanName = handlerBean instanceof String
						? (String) handlerBean
						: findLocalBeanName(childContext, handlerBean);
				if (handlerBeanName != null
						&& Arrays.asList(CONTROLLER_BEANS).contains(handlerBeanName)) {
					registeredControllers.add(handlerBeanName);
				}
			}
		}
		for (String controllerBean : CONTROLLER_BEANS) {
			assertTrue("Child handler mappings must contain " + controllerBean,
					registeredControllers.contains(controllerBean));
		}

		Set<String> rootMappings = reportHandlerMappingsForContext("ROOT", rootContext);
		Set<String> childMappings = reportHandlerMappingsForContext("CHILD", childContext);

		Set<String> duplicates = new TreeSet<String>(rootMappings);
		duplicates.retainAll(childMappings);
		System.out.println("Mappings registered in both contexts: " + duplicates.size());
		for (String mapping : duplicates) {
			System.out.println("  " + mapping);
		}
		assertTrue("Controller URL mappings must not be registered in both contexts",
				duplicates.isEmpty());
	}

	private Set<String> reportHandlerMappingsForContext(String contextName,
			ConfigurableApplicationContext context) {
		System.out.println("[" + contextName + "]");
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		String[] mappingBeanNames = beanFactory.getBeanNamesForType(
				RequestMappingHandlerMapping.class, true, false);
		Arrays.sort(mappingBeanNames);
		Set<String> signatures = new TreeSet<String>();

		if (mappingBeanNames.length == 0) {
			System.out.println("  No local RequestMappingHandlerMapping beans");
		}

		for (String mappingBeanName : mappingBeanNames) {
			RequestMappingHandlerMapping mapping = (RequestMappingHandlerMapping)
					getBean(context, mappingBeanName, contextName);
			Map<RequestMappingInfo, HandlerMethod> handlerMethods =
					mapping.getHandlerMethods();
			System.out.printf("  %s | registered handler methods=%d%n",
					mappingBeanName, handlerMethods.size());

			for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
					: handlerMethods.entrySet()) {
				HandlerMethod handlerMethod = entry.getValue();
				Object handlerBean = handlerMethod.getBean();
				String handlerBeanName = handlerBean instanceof String
						? (String) handlerBean
						: findLocalBeanName(context, handlerBean);
				String signature = entry.getKey() + " -> "
						+ handlerMethod.getBeanType().getName() + "#"
						+ handlerMethod.getMethod().toGenericString();
				signatures.add(signature);
				System.out.printf("    bean=%s | controller=%s | method=%s | %s%n",
						handlerBeanName, handlerMethod.getBeanType().getName(),
						handlerMethod.getMethod().toGenericString(),
						entry.getKey());
			}
		}
		return signatures;
	}

	private Object getBean(ConfigurableApplicationContext context, String beanName,
			String contextName) {
		try {
			return context.getBeanFactory().getBean(beanName);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Could not resolve bean "
					+ contextName + "." + beanName, exception);
		}
	}

	private Set<String> localBeanNames(ConfigurableApplicationContext context) {
		return new TreeSet<String>(Arrays.asList(
				context.getBeanFactory().getBeanDefinitionNames()));
	}

	private List<Field> findDependencyFields(Class<?> type) {
		List<Field> fields = new ArrayList<Field>();
		for (Class<?> current = type; current != null && current != Object.class;
				current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (field.isAnnotationPresent(Autowired.class) || isApplicationDependency(field.getType())) {
					fields.add(field);
				}
			}
		}
		return fields.isEmpty() ? Collections.<Field>emptyList() : fields;
	}

	private boolean isApplicationDependency(Class<?> type) {
		String name = type.getName();
		return name.startsWith("com.defermentsystem.service.")
				|| name.startsWith("com.defermentsystem.repository.")
				|| name.equals("com.defermentsystem.domain.Mail");
	}

	private Object unwrapProxy(Object bean, String contextName, String beanName) {
		Object target = bean;
		while (target instanceof Advised) {
			TargetSource targetSource = ((Advised) target).getTargetSource();
			if (!targetSource.isStatic()) {
				break;
			}
			try {
				Object nextTarget = targetSource.getTarget();
				if (nextTarget == null || nextTarget == target) {
					break;
				}
				target = nextTarget;
			} catch (Exception exception) {
				throw new IllegalStateException("Could not unwrap proxy "
						+ contextName + "." + beanName, exception);
			}
		}
		return target;
	}

	private String findOwningContext(Object bean,
			ConfigurableApplicationContext rootContext,
			ConfigurableApplicationContext childContext) {
		String childBeanName = findLocalBeanName(childContext, bean);
		if (childBeanName != null) {
			return "CHILD:" + childBeanName;
		}
		String rootBeanName = findLocalBeanName(rootContext, bean);
		if (rootBeanName != null) {
			return "ROOT:" + rootBeanName;
		}
		return "not a local context bean";
	}

	private String findLocalBeanName(ConfigurableApplicationContext context, Object bean) {
		for (String beanName : context.getBeanFactory().getBeanDefinitionNames()) {
			if (context.getBeanFactory().getBean(beanName) == bean) {
				return beanName;
			}
		}
		return null;
	}

	private static class TrackingDispatcherServlet extends DispatcherServlet {

		private static final long serialVersionUID = 1L;
		private ConfigurableWebApplicationContext createdContext;

		@Override
		protected WebApplicationContext createWebApplicationContext(
				WebApplicationContext parent) {
			WebApplicationContext context = super.createWebApplicationContext(parent);
			createdContext = (ConfigurableWebApplicationContext) context;
			return context;
		}

		private ConfigurableWebApplicationContext getCreatedContext() {
			return createdContext;
		}
	}
}
