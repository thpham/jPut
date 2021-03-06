package cz.encircled.jput.runner

import cz.encircled.jput.TestReporter
import cz.encircled.jput.context.context
import cz.encircled.jput.runner.junit.JPutJUnit4Runner
import cz.encircled.jput.runner.junit.JPutSpringRunner
import cz.encircled.jput.runner.steps.Junit4TestRunnerSteps
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import kotlin.test.*

class TestRunListener : RunListener() {

    val failures = mutableListOf<Failure>()

    val finished = mutableListOf<Description>()

    val assumptionFailures = mutableListOf<Failure>()

    override fun testAssumptionFailure(failure: Failure) {
        assumptionFailures.add(failure)
    }

    override fun testFinished(description: Description) {
        finished.add(description)
    }

    override fun testFailure(failure: Failure) {
        failures.add(failure)
    }

}

class Junit4TestRunnerIntegrationTest : BaseIntegrationTest() {

    companion object {

        @JvmStatic
        @BeforeClass
        fun runSteps() {
            val runner = JPutJUnit4Runner(Junit4TestRunnerSteps::class.java)
            val notifier = RunNotifier()
            notifier.addListener(listener)
            runner.run(notifier)
        }

    }

}


class SpringTestRunnerIntegrationTest : BaseIntegrationTest() {

    companion object {

        @JvmStatic
        @BeforeClass
        fun runSteps() {
            val runner = JPutSpringRunner(Junit4TestRunnerSteps::class.java)
            val notifier = RunNotifier()
            notifier.addListener(listener)
            runner.run(notifier)
        }

    }

}

abstract class BaseIntegrationTest {

    companion object {

        lateinit var listener: TestRunListener

        @JvmStatic
        @BeforeClass
        fun before() {
            Junit4TestRunnerSteps.testCounter = 0
            listener = TestRunListener()
            context.init()

            (context.resultReporters[0] as TestReporter).executions.clear()
            (context.resultReporters[0] as TestReporter).invocations.clear()
            Junit4TestRunnerSteps.isBeforeExecuted = false
        }

    }

    // TODO other as well?
    @Test
    fun testBeforeTestExecuted() {
        assertTrue(Junit4TestRunnerSteps.isBeforeExecuted)
    }

    @Test
    fun testAssumptionFailedPropagated() {
        assertEquals(1, listener.assumptionFailures.size)
        assertEquals("testAssumptionFailedPropagated", listener.assumptionFailures[0].description.methodName)
    }

    @Test
    fun testWarmUpAndRepeatsCount() {
        assertSuccessful("testWarmUpAndRepeatsCount")
        assertEquals(4, Junit4TestRunnerSteps.testCounter)
    }

    @Test
    fun testIgnoredTest() {
        assertFalse(isTestExecuted("testIgnoredTest"))
    }

    @Test
    fun testCurrentSuiteIsSet() {
        assertEquals(Junit4TestRunnerSteps::class.java, context.currentSuite!!.clazz)
        assertFalse(context.currentSuite!!.isParallel)
    }

    @Test
    fun testReturnedErrorPropagated() {
        val expected = "Limit exceptions count = 1, actual = 1000"
        assertFailedAssertion("testReturnedErrorPropagated", expected)
        assertExceptionsInRuns("testRuntimeExceptionCatched", "Test exception")

        val details = getExecution("testReturnedErrorPropagated").executionResult
                .values.map { it.resultDetails }

        // Assert that results are correctly stored in parallel execution

        details.forEach {
            assertEquals("Test exception ${it.resultCode}", it.errorMessage)
        }

        assertEquals(1000, details.map { it.resultCode }.distinct().size)
    }

    @Test
    fun testRuntimeExceptionCatched() {
        val expected = "Limit exceptions count = 1, actual = 2"
        assertFailedAssertion("testRuntimeExceptionCatched", expected)
        assertExceptionsInRuns("testRuntimeExceptionCatched", "Test exception")
    }

    @Test
    fun testMarkPerformanceTestStart() {
        assertSuccessful("testMarkPerformanceTestStart")

        assertEquals(1, getExecution("testMarkPerformanceTestStart").getElapsedTimes().size)

        // Test has thread.sleep(50) and then calls 'markPerformanceTestStart'
        assertTrue(getExecution("testMarkPerformanceTestStart").getElapsedTimes()[0] < 40)
    }

    @Test
    @Ignore // FIXME
    fun testMarkPerformanceTestEnd() {
        assertSuccessful("testMarkPerformanceTestEnd")

        assertEquals(1, getExecution("testMarkPerformanceTestEnd").getElapsedTimes().size)

        // Test calls 'markPerformanceTestEnd' and then thread.sleep(50)
        assertTrue(getExecution("testMarkPerformanceTestEnd").getElapsedTimes()[0] < 40)
    }

    @Test
    fun testReporterIsInvokedCorrectly() {
        val reporter = context.resultReporters[0] as TestReporter

        assertEquals(
                mutableListOf<Pair<String, Any?>>(
                        "beforeClass" to Junit4TestRunnerSteps::class.java,

                        "beforeTest" to "Junit4TestRunnerSteps#testAssumptionFailedPropagated",

                        "beforeTest" to "Junit4TestRunnerSteps#testMarkPerformanceTestEnd",
                        "afterTest" to "Junit4TestRunnerSteps#testMarkPerformanceTestEnd",

                        "beforeTest" to "Junit4TestRunnerSteps#testMarkPerformanceTestStart",
                        "afterTest" to "Junit4TestRunnerSteps#testMarkPerformanceTestStart",

                        "beforeTest" to "Junit4TestRunnerSteps#testReturnedErrorPropagated",
                        "afterTest" to "Junit4TestRunnerSteps#testReturnedErrorPropagated",

                        "beforeTest" to "Junit4TestRunnerSteps#testRuntimeExceptionCatched",
                        "afterTest" to "Junit4TestRunnerSteps#testRuntimeExceptionCatched",

                        "beforeTest" to "Junit4TestRunnerSteps#testWarmUpAndRepeatsCount",
                        "afterTest" to "Junit4TestRunnerSteps#testWarmUpAndRepeatsCount",

                        "afterClass" to Junit4TestRunnerSteps::class.java
                ),
                reporter.invocations
        )
    }

    /**
     * Assert that unit test failed with given [expectedAssertion]
     */
    fun assertFailedAssertion(method: String, expectedAssertion: String) {
        val failure = expectFailure(method)
        assertEquals("Performance test failed.\n[$expectedAssertion]", failure.exception.message)

        val execution = getExecution(method)

        val actualViolationMsg = execution.violations.joinToString {
            it.messageProducer.invoke(execution)
        }
        assertEquals(expectedAssertion, actualViolationMsg)
    }

    fun assertExceptionsInRuns(method: String, expectedError: String, resultCode: Int = 500) {
        val execution = getExecution(method)

        execution.executionResult.values.forEach {
            assertEquals(resultCode, it.resultDetails.resultCode)
            assertEquals(expectedError, it.resultDetails.errorMessage)
            assertEquals(expectedError, it.resultDetails.error!!.message)
        }
    }

    private fun assertSuccessful(method: String) {
        assertTrue(listener.failures
                .map { it.description.methodName }
                .all { it != method })

        val execution = getExecution(method)
        assertTrue(execution.violations.isEmpty())
        assertTrue(execution.executionResult.values.all {
            it.resultDetails.resultCode == 200 &&
                    it.resultDetails.error == null && it.resultDetails.errorMessage == null
        })

        assertTrue(isTestExecuted(method))
    }

    private fun getExecution(id: String) =
            (context.resultReporters[0] as TestReporter).getExecution(id)

    private fun expectFailure(method: String): Failure =
            listener.failures.firstOrNull {
                it.description.methodName == method
            } ?: fail("Assertion failure is expected for $method")

    private fun isTestExecuted(method: String): Boolean {
        return listener.finished
                .map { it.methodName }
                .any { it == method }
    }

}

@Configuration
@ComponentScan(basePackages = ["cz.encircled.jput.spring.test"])
open class Conf