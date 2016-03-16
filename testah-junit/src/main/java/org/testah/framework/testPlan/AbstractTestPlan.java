package org.testah.framework.testPlan;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testah.TS;
import org.testah.framework.annotations.TestMeta;
import org.testah.framework.dto.StepActionDto;
import org.testah.framework.dto.TestCaseDto;
import org.testah.framework.dto.TestPlanDto;
import org.testah.framework.dto.TestStepDto;
import org.testah.framework.report.JUnitFormatter;

public abstract class AbstractTestPlan {

	private static ThreadLocal<TestPlanDto> testPlan;
	private static ThreadLocal<TestCaseDto> testCase;
	private static ThreadLocal<TestStepDto> testStep;

	public TestName name = new TestName();

	public TestRule globalTimeout = Timeout.millis(100000L);

	public ExternalResource initialize = new ExternalResource() {

		protected void before() throws Throwable {
			initlizeTest();
		}

		protected void after() {
			tearDownTest();
		};
	};

	public abstract void initlizeTest();

	public abstract void tearDownTest();

	public TestWatcher filter = new TestWatcher() {

		public Statement apply(final Statement base, final Description description) {
			final String onlyRun = System.getProperty("only_run");
			Assume.assumeTrue(onlyRun == null
					|| Arrays.asList(onlyRun.split(",")).contains(description.getTestClass().getSimpleName()));
			final String mth = System.getProperty("method");
			Assume.assumeTrue(mth == null || Arrays.asList(mth.split(",")).contains(description.getMethodName()));
			return super.apply(base, description);
		}
	};

	public TestWatcher watchman2 = new TestWatcher() {

		protected void failed(final Throwable e, final Description description) {
			TS.log().error("TestCase Status: failed");
			stopTestCase(false);
		}

		protected void succeeded(final Description description) {
			TS.log().info("TestCase Status: passed");
			stopTestCase(null);

			try {
				final Test testAnnotation = description.getAnnotation(Test.class);
				if (null != testAnnotation && None.class == testAnnotation.expected()) {
					if (null != getTestCase()) {

						getTestCase().getAssertionError();
					}
				}
			} catch (final AssertionError ae) {
				TS.log().error("Exception Thrown Looking at TestCase Assert History\n" + ae.getMessage());
				throw ae;
			}

		}

		protected void finished(final Description desc) {
			TS.log().info("Finished TestCase: " + desc.getDisplayName() + " - thread[" + Thread.currentThread().getId()
					+ "]");
		}

		protected void starting(final Description desc) {

			if (!didTestPlanStart()) {
				TS.log().info("starting TestPlan:" + desc.getTestClass().getName() + " - thread["
						+ Thread.currentThread().getId() + "]");
				startTestPlan(desc.getTestClass().getAnnotation(TestMeta.class));
			}
			TS.log().info(
					"starting TestCase:" + desc.getDisplayName() + " - thread[" + Thread.currentThread().getId() + "]");
			startTestCase(desc.getAnnotation(TestMeta.class));
		}
	};

	@Rule
	public TestRule chain = RuleChain.outerRule(watchman2).around(initialize).around(name).around(filter);

	@BeforeClass
	public static void setupBase() {
		try {

		} catch (final Exception e) {

		}
	}

	@AfterClass
	public static void tearDownBase() {
		try {
			stopTestPlan();
			TS.util().toJsonPrint(getTestPlan());
			new JUnitFormatter(getTestPlan()).createReport();
		} catch (final Exception e) {
			TS.log().error("after testplan", e);
		}
	}

	public abstract void doOnFail();

	public abstract void doOnPass();

	private static boolean testPlanStart = false;

	private static boolean didTestPlanStart() {
		return testPlanStart;
	}

	public static ThreadLocal<TestPlanDto> getTestPlanThreadLocal() {
		if (null == testPlan) {
			testPlan = new ThreadLocal<TestPlanDto>();
		}
		return testPlan;
	}

	public static TestPlanDto getTestPlan() {
		return getTestPlanThreadLocal().get();
	}

	public static ThreadLocal<TestCaseDto> getTestCaseThreadLocal() {
		if (null == testCase) {
			testCase = new ThreadLocal<TestCaseDto>();
		}
		return testCase;
	}

	public static TestCaseDto getTestCase() {
		return getTestCaseThreadLocal().get();
	}

	public static TestStepDto getTestStep() {
		if (null == testStep) {
			testStep = new ThreadLocal<TestStepDto>();
		}
		if (null == testStep.get() && null != getTestCase()) {
			AbstractTestPlan.testStep.set(new TestStepDto("Initial Step", "").start());
		}
		return testStep.get();
	}

	protected TestPlanDto startTestPlan(final TestMeta testPlan) {
		getTestPlanThreadLocal().set(new TestPlanDto(testPlan).start());
		AbstractTestPlan.testPlanStart = true;
		return AbstractTestPlan.testPlan.get();
	}

	protected static void stopTestPlan() {
		getTestPlan().stop();
		AbstractTestPlan.testPlanStart = false;
	}

	private TestCaseDto startTestCase(final TestMeta testCase) {
		if (didTestPlanStart()) {
			getTestCaseThreadLocal().set(new TestCaseDto(testCase).start());
		}
		return getTestCase();
	}

	protected static Boolean stopTestCase() {
		return stopTestCase(null);
	}

	protected static Boolean stopTestCase(final Boolean status) {
		if (null != getTestCase()) {
			stopTestStep();
			getTestPlan().addTestCase(getTestCase().stop(status));
			return getTestCase().getStatus();
		}
		return null;
	}

	protected static TestStepDto startTestStep(final TestStepDto testStep) {
		if (didTestPlanStart() && null != getTestCase()) {
			stopTestStep();
			AbstractTestPlan.testStep.set(testStep.start());
		}
		return getTestStep();
	}

	protected static void stopTestStep() {
		if (null != getTestStep()) {
			getTestCase().addTestStep(getTestStep().stop());
		}
	}

	public static boolean addAssertHistory(final StepActionDto assertHistoryItem) {
		if (null == getTestStep()) {
			return false;
		}
		getTestStep().addAssertHistory(assertHistoryItem);
		return true;
	}

	public static List<StepActionDto> getAssertHistory() {
		if (null == getTestStep()) {
			return null;
		}
		return getTestStep().getStepActions();
	}

}
