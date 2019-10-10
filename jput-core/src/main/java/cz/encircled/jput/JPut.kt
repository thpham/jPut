package cz.encircled.jput

import cz.encircled.jput.context.ConfigurationBuilder
import cz.encircled.jput.context.context
import cz.encircled.jput.model.ExecutionRunResultDetails
import cz.encircled.jput.model.PerfTestExecution
import org.slf4j.LoggerFactory

/**
 * TODO list:
 * - Delete old entries
 * - Timeout when max time is not set
 * - Self warm up
 * - Logging
 *
 * Helper functions for JPut users to control the perf tests execution
 *
 * @author Vlad on 15-Sep-19.
 */
object JPut {

    private val log = LoggerFactory.getLogger(JPut::class.java)

    /**
     * This function may be called directly from the performance test to tell JPut that measurement must start from this point,
     * i.e. not from the beginning of the method. This might be useful when performance test makes some expensive initialization first.
     *
     * For example:
     *
     * ```
     * @PerformanceTest(...)
     * public void myPerfTest() {
     *     prepareTestData(); // takes some time...
     *
     *     JPut.markPerformanceTestStart(); // Ignore time took by the init
     *
     *     // and here goes the code which will really be measured
     *     ...
     * }
     * ```
     *
     * Anonymous functions are not supported yet!
     */
    fun markPerformanceTestStart() {
        val execution = getCurrentExecution()

        if (execution == null) {
            log.warn("Ignoring [markPerformanceTestStart] since it is called from non JPut test")
        } else {
            execution.resetCurrentExecutionStartTime()
        }
    }

    /**
     * Anonymous functions are not supported yet!
     */
    fun setPerformanceTestResult(result: ExecutionRunResultDetails) {
        val execution = getCurrentExecution()

        if (execution == null) {
            log.warn("Ignoring [setPerformanceTestResult] since it is called from non JPut test")
        } else {
            execution.getCurrentRun().resultDetails = result
        }
    }

    @JvmOverloads
    fun setPerformanceTestResult(error: Throwable? = null, resultCode: Int = 500, errorMessage: String? = error?.message) =
            setPerformanceTestResult(ExecutionRunResultDetails(resultCode, error, errorMessage))

    @JvmOverloads
    fun setPerformanceTestResult(resultCode: Int, errorMessage: String? = null) =
            setPerformanceTestResult(ExecutionRunResultDetails(resultCode, errorMessage = errorMessage))

    fun getCurrentExecution(): PerfTestExecution? {
        val method = context.currentSuiteMethod ?: throw IllegalStateException("Whoops, looks like you are not using [JPutJUnit4Runner]...")

        check(context.currentSuite == null || !context.currentSuite!!.isParallel) {
            "When running in parallel, it is needed to pass testId argument to all [JPut.*] methods"
        }

        val defaultTestId = ConfigurationBuilder.defaultTestId(method)

        val testId = context.customTestIds[defaultTestId] ?: defaultTestId

        return context.testExecutions[testId]
    }

}