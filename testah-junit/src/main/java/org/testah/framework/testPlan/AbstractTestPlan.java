package org.testah.framework.testPlan;

import org.junit.*;
import org.junit.Test.None;
import org.junit.rules.*;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.MDC;
import org.testah.TS;
import org.testah.client.dto.StepActionDto;
import org.testah.client.dto.TestCaseDto;
import org.testah.client.dto.TestPlanDto;
import org.testah.client.dto.TestStepDto;
import org.testah.framework.annotations.KnownProblem;
import org.testah.framework.annotations.TestCase;
import org.testah.framework.annotations.TestPlan;
import org.testah.framework.cli.Cli;
import org.testah.framework.cli.TestFilter;
import org.testah.framework.dto.TestDtoHelper;
import org.testah.runner.TestahJUnitRunner;
import org.testah.runner.testPlan.TestPlanActor;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * The Class AbstractTestPlan.
 */
public abstract class AbstractTestPlan {

    /**
     * The test plan.
     */
    private static ThreadLocal<TestPlanDto> testPlan = new ThreadLocal<>();

    /**
     * The test case.
     */
    private static ThreadLocal<TestCaseDto> testCase = new ThreadLocal<>();

    /**
     * The test step.
     */
    private static ThreadLocal<TestStepDto> testStep = new ThreadLocal<>();

    /**
     * The test plan start.
     */
    private static ThreadLocal<Boolean> testPlanStart = new ThreadLocal<>();

    /**
     * The test filter.
     */
    private static TestFilter testFilter = null;

    /**
     * The ignored tests.
     */
    private static ThreadLocal<HashMap<String, String>> ignoredTests = null;

    /**
     * The name.
     */
    public TestName name = new TestName();

    /**
     * The assume true.
     */
    private boolean assumeTrue = false;

    /**
     * The global timeout.
     */
    private TestRule globalTimeout = new Timeout(100000);
    /**
     * The description.
     */
    private Description description;
    /**
     * The filter.
     */
    public TestWatcher filter = new TestWatcher() {

        public Statement apply(final Statement base, final Description description) {
            setDescription(description);
            return super.apply(base, description);
        }
    };
    /**
     * The initialize.
     */
    private ExternalResource initialize = new ExternalResource() {

        protected void before() throws Throwable {
            filterTest(description);
            initializeTest();
        }

        protected void after() {
            tearDownTest();
        }

        ;
    };
    /**
     * The watchman2.
     */
    private TestWatcher watchman2 = new TestWatcher() {

        protected void failed(final Throwable e, final Description description) {
            TS.step().action().createAssertResult("Unexpected Error occurred", false, "UnhandledExceptionFoundByJUnit", "",
                    e.getMessage(), e).log();

            TS.log().error("TESTCASE Status: failed", e);
            stopTestCase(false);
        }

        protected void succeeded(final Description description) {
            stopTestCase((TS.params().isResultIgnoredIfNoAssertsFound() ? null : true));
            TS.log().info("TESTCASE Status: " + getTestCase().getStatusEnum());
            try {
                final Test testAnnotation = description.getAnnotation(Test.class);
                if (null != testAnnotation && None.class == testAnnotation.expected()) {
                    if (null != getTestCase()) {
                        getTestCase().getAssertionError();
                        if (null == getTestCase().getStatus()) {
                            addIgnoredTest(description.getClassName() + "#" + description.getMethodName(),
                                    "NA_STATUS_NO_ASSERTS");
                            return;
                        }
                    }
                }
            } catch (final AssertionError ae) {
                TS.log().error("Exception Thrown Looking at TestCase Assert History\n" + ae.getMessage());
                throw ae;
            }
        }

        protected void finished(final Description desc) {
            TS.log().info("TESTCASE Complete: " + desc.getDisplayName() + " - thread[" + Thread.currentThread().getId() + "]");
            if (null == getTestCase().getStatus()) {
                return;
            } else if (getTestCase().getStatus()) {
                doOnPass();
            } else {
                doOnFail();
            }
        }

        protected void starting(final Description desc) {
            if (!didTestPlanStart()) {

                setUpThreadLocals();

                TS.log().info("TESTPLAN started:" + desc.getTestClass().getName() + " - thread[" + Thread.currentThread().getId() + "]");
                final TestPlan testPlan = desc.getTestClass().getAnnotation(TestPlan.class);
                if (null == desc.getTestClass().getAnnotation(TestPlan.class)) {
                    TS.log().warn("Missing @TestPlan annotation!");
                }
                startTestPlan(desc, testPlan, desc.getTestClass().getAnnotation(KnownProblem.class));
                getTestPlan().setRunInfo(TestDtoHelper.createRunInfo());

                for (final Method m : desc.getTestClass().getDeclaredMethods()) {
                    if (null != m.getAnnotation(Ignore.class)) {
                        addIgnoredTest(desc.getClassName() + "#" + m.getName(), "JUNIT_IGNORE");
                    }
                }
                MDC.put("logFileName", "" + Thread.currentThread().getId());
            }
            TS.log().info(Cli.BAR_LONG);

            TS.log().info(
                    "TESTCASE started:" + desc.getDisplayName() + " - thread[" + Thread.currentThread().getId() + "]");
            startTestCase(desc, desc.getAnnotation(TestCase.class), desc.getTestClass().getAnnotation(TestPlan.class),
                    desc.getAnnotation(KnownProblem.class));
            getTestStep();
        }
    };
    /**
     * The chain.
     */
    @Rule
    public TestRule chain = RuleChain.outerRule(watchman2).around(initialize).around(name).around(filter);

    /**
     * Setup abstract test plan.
     */
    @BeforeClass
    public static void setupAbstractTestPlan() {
        setUpThreadLocals();
    }

    /**
     * Sets up thread locals.
     */
    public static void setUpThreadLocals() {
        setUpThreadLocals(false);
    }

    /**
     * Set up ThreadLocals.
     *
     * @param override the override
     */
    public static void setUpThreadLocals(final boolean override) {
        if (override || !TestahJUnitRunner.isInUse()) {
            testPlan = new ThreadLocal<>();
            testCase = new ThreadLocal<>();
            testStep = new ThreadLocal<>();
            testPlanStart = new ThreadLocal<>();
            ignoredTests = new ThreadLocal<>();
        }
    }

    /**
     * Tear down abstract test plan.
     */
    @AfterClass
    public static void tearDownAbstractTestPlan() {
        try {
            if (null != getTestPlan()) {
                getTestPlan().stop();
            }
            if (!TestPlanActor.isResultsInUse()) {
                TS.getTestPlanReporter().reportResults(getTestPlan());
            }
            if (!TestahJUnitRunner.isInUse()) {
                cleanUpTestplanThreadLocal();
            }

            if (!TestPlanActor.isResultsInUse()) {
                tearDownTestah();
            }
        } catch (final Exception e) {
            TS.log().error("after testplan", e);
        }
    }

    /**
     * Gets the test plan.
     *
     * @return the test plan
     */
    public static TestPlanDto getTestPlan() {
        return getTestPlanThreadLocal().get();
    }

    /**
     * Clean up all ThreadLocal.
     */
    public static void cleanUpTestplanThreadLocal() {
        TS.cleanUpThreadLocal(testPlan);
        TS.cleanUpThreadLocal(testCase);
        TS.cleanUpThreadLocal(testStep);
        TS.cleanUpThreadLocal(testPlanStart);
        TS.cleanUpThreadLocal(ignoredTests);
    }

    /**
     * Clean up when Testah is done.
     */
    public static void tearDownTestah() {
        if (TS.isBrowser()) {
            TS.browser().close();
        }
        TS.setBrowser(null);
        TS.tearDown();
    }

    /**
     * Gets the test plan thread local.
     *
     * @return the test plan thread local
     */
    private static ThreadLocal<TestPlanDto> getTestPlanThreadLocal() {
        if (null == testPlan) {
            testPlan = new ThreadLocal<>();
        }
        return testPlan;
    }

    /**
     * Stop test plan.
     */
    public static void stopTestPlan() {
        setTestPlanStart(false);
    }

    /**
     * Stop test case.
     *
     * @param status the status
     */
    protected static void stopTestCase(final Boolean status) {
        if (null != getTestCase()) {
            stopTestStep();
            getTestPlan().addTestCase(getTestCase().stop(status));
        }
    }

    /**
     * Stop test step.
     */
    protected static void stopTestStep() {
        if (null != getTestStep()) {
            getTestCase().addTestStep(getTestStep().stop());
            testStep.set(null);
        }
    }

    /**
     * Gets the test step.
     *
     * @return the test step
     */
    public static TestStepDto getTestStep() {

        if (null == getTestStepThreadLocal().get()) {
            AbstractTestPlan.testStep.set(new TestStepDto("Initial Step", "").start());
            TS.log().info("TESTSTEP - " + AbstractTestPlan.testStep.get().getName());
        }
        return getTestStepThreadLocal().get();
    }

    /**
     * Gets the test step thread local.
     *
     * @return the test step thread local
     */
    public static ThreadLocal<TestStepDto> getTestStepThreadLocal() {
        if (null == testStep) {
            testStep = new ThreadLocal<>();
        }
        return testStep;
    }

    /**
     * Start test step.
     *
     * @param testStep the test step
     * @return the test step dto
     */
    public static TestStepDto startTestStep(final TestStepDto testStep) {
        if (didTestPlanStart() && null != getTestCase()) {
            stopTestStep();
            getTestStepThreadLocal().set(testStep.start());
            TS.log().info("TESTSTEP - " + testStep.getName() + " " + testStep.getDescription());
        }
        return getTestStep();
    }

    /**
     * Gets the test filter.
     *
     * @return the test filter
     */
    public static TestFilter getTestFilter() {
        if (null == testFilter) {
            testFilter = new TestFilter();
        }
        return testFilter;
    }

    /**
     * Sets the test filter.
     *
     * @param testFilter the new test filter
     */
    public static void setTestFilter(final TestFilter testFilter) {
        AbstractTestPlan.testFilter = testFilter;
    }

    /**
     * Adds the ignored test.
     *
     * @param testCaseName the test case name
     * @param reason       the reason
     */
    public static void addIgnoredTest(final String testCaseName, final String reason) {
        getIgnoredTests().put(testCaseName, reason);
    }

    /**
     * Gets the ignored tests.
     *
     * @return the ignored tests
     */
    public static HashMap<String, String> getIgnoredTests() {
        final ThreadLocal<HashMap<String, String>> ignoredTestsTmp;
        if (null == ignoredTests) {
            ignoredTestsTmp = new ThreadLocal<>();
            ignoredTestsTmp.set(new HashMap<String, String>());
            ignoredTests = ignoredTestsTmp;
        }
        if (null == ignoredTests.get()) {
            ignoredTests.set(new HashMap<String, String>());
        }
        return ignoredTests.get();
    }

    /**
     * Sets the test plan start.
     *
     * @param testPlanStart the new test plan start
     */
    private static void setTestPlanStart(final boolean testPlanStart) {
        AbstractTestPlan.testPlanStart.set(testPlanStart);
    }

    /**
     * Did test plan start.
     *
     * @return true, if successful
     */
    protected static boolean didTestPlanStart() {
        if (null == testPlanStart.get()) {
            testPlanStart.set(false);
        }
        return testPlanStart.get();
    }

    /**
     * Gets the test case thread local.
     *
     * @return the test case thread local
     */
    private static ThreadLocal<TestCaseDto> getTestCaseThreadLocal() {
        if (null == testCase) {
            testCase = new ThreadLocal<>();
        }
        return testCase;
    }

    /**
     * Gets the test case.
     *
     * @return the test case
     */
    private static TestCaseDto getTestCase() {
        return getTestCaseThreadLocal().get();
    }

    /**
     * Gets global timeout.
     *
     * @return the global timeout
     */
    public TestRule getGlobalTimeout() {
        return globalTimeout;
    }

    /**
     * Initialize test.
     */
    public abstract void initializeTest();

    /**
     * Tear down test.
     */
    public abstract void tearDownTest();

    /**
     * Filter test.
     *
     * @param description the description
     */
    private void filterTest(final Description description) {
        final String name = description.getClassName() + "#" + description.getMethodName();
        final KnownProblem kp = description.getAnnotation(KnownProblem.class);
        setAssumeTrue(false);
        TestCaseDto test = new TestCaseDto();
        test = TestDtoHelper.fill(test, description.getAnnotation(TestCase.class), kp,
                description.getTestClass().getAnnotation(TestPlan.class));
        if (!getTestFilter().filterTestCase(test, name)) {
            addIgnoredTest(name, "METADATA_FILTER");
            setAssumeTrue(true);
            Assume.assumeTrue("Filtered out, For details use Trace level logging" +
                    "\nCheck your filter settings in Testah.properties for " +
                    "filter_DEFAULT_filterIgnoreKnownProblem", false);
        }

        if (null != TS.params().getFilterIgnoreKnownProblem()) {
            if (null != kp) {
                if ("true".equalsIgnoreCase(TS.params().getFilterIgnoreKnownProblem())) {
                    setAssumeTrue(true);
                    addIgnoredTest(name, "KNOWN_PROBLEM_FILTER");
                    Assume.assumeTrue("Filtered out, KnownProblem found: " + kp.description() +
                            "\nCheck your filter settings in Testah.properties for " +
                            "filter_DEFAULT_filterIgnoreKnownProblem", false);
                }
            } else if ("false".equalsIgnoreCase(TS.params().getFilterIgnoreKnownProblem())) {
                setAssumeTrue(true);
                addIgnoredTest(name, "KNOWN_PROBLEM_FILTER");
                Assume.assumeTrue(
                        "Filtered out, KnownProblem Not found and is required\nCheck your filter" +
                                " settings in Testah.properties for filter_DEFAULT_filterIgnoreKnownProblem",
                        false);
            }
        }
    }

    /**
     * Gets chain.
     *
     * @return the chain
     */
    public TestRule getChain() {
        return chain;
    }

    /**
     * Do on fail.
     */
    public abstract void doOnFail();

    /**
     * Do on pass.
     */
    public abstract void doOnPass();

    /**
     * Start test plan.
     *
     * @param desc        the desc
     * @param testPlan    the test plan
     * @param knowProblem the know problem
     * @return the test plan dto
     */
    protected TestPlanDto startTestPlan(final Description desc, final TestPlan testPlan, final KnownProblem knowProblem) {
        getTestPlanThreadLocal().set(TestDtoHelper.createTestPlanDto(desc, testPlan, knowProblem).start());
        setTestPlanStart(true);
        return AbstractTestPlan.testPlan.get();
    }

    /**
     * Start test case.
     *
     * @param desc        the desc
     * @param testCase    the test case
     * @param testPlan    the test plan
     * @param knowProblem the know problem
     * @return the test case dto
     */
    protected TestCaseDto startTestCase(final Description desc, final TestCase testCase, final TestPlan testPlan,
                                      final KnownProblem knowProblem) {
        if (didTestPlanStart()) {
            getTestCaseThreadLocal()
                    .set(TestDtoHelper.createTestCaseDto(desc, testCase, knowProblem, testPlan).start());
        }
        return getTestCase();
    }

    /**
     * Step.
     *
     * @return the test step dto
     */
    public TestStepDto step() {
        return step("Step");
    }

    /**
     * Step.
     *
     * @param name the name
     * @return the test step dto
     */
    public TestStepDto step(final String name) {
        final TestStepDto s = new TestStepDto();
        s.setName(name);
        return startTestStep(s);
    }

    /**
     * Step.
     *
     * @param name        the name
     * @param description the description
     * @return the test step dto
     */
    public TestStepDto step(final String name, final String description) {
        final TestStepDto s = new TestStepDto();
        s.setName(name);
        s.setDescription(description);
        return startTestStep(s);
    }

    /**
     * Step action.
     *
     * @return the step action dto
     */
    public StepActionDto stepAction() {
        return new StepActionDto();
    }

    /**
     * Step action info.
     *
     * @param message1 the message1
     * @return the step action dto
     */
    public StepActionDto stepActionInfo(final String message1) {
        return TS.step().action().createInfo(message1);
    }

    /**
     * Data value.
     *
     * @param value the value
     * @return the abstract test plan
     */
    public AbstractTestPlan dataValue(final String value) {
        if (null == value) {
            getTestCase().setDataValue("");
        } else if (value.length() > 255) {
            TS.log().debug("Data Value can only be 255 chars, truncating value");
            getTestCase().setDataValue(value.substring(0, 254));
        } else {
            getTestCase().setDataValue(value);
        }
        return this;
    }

    /**
     * Checks if is assume true.
     *
     * @return true, if is assume true
     */
    public boolean isAssumeTrue() {
        return assumeTrue;
    }

    /**
     * Sets the assume true.
     *
     * @param assumeTrue the assume true
     * @return the abstract test plan
     */
    public AbstractTestPlan setAssumeTrue(final boolean assumeTrue) {
        this.assumeTrue = assumeTrue;
        return this;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public Description getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     * @return the abstract test plan
     */
    public AbstractTestPlan setDescription(final Description description) {
        this.description = description;
        return this;
    }

    /**
     * Reset the test case.
     *
     * @param reasonWhy explanation why test case was reset
     * @return this object
     */
    public AbstractTestPlan resetTestCase(final String reasonWhy) {
        getTestCase().getTestSteps().clear();
        getTestStepThreadLocal().set(new TestStepDto("Resetting TestCase And Going To Retry", reasonWhy).start());
        return this;
    }
}
