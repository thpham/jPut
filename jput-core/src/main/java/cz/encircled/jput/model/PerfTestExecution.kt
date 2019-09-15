package cz.encircled.jput.model

import cz.encircled.jput.Statistics


/**
 * Represents the execution state of a particular performance test
 *
 * @author Vlad on 20-May-17.
 */
data class PerfTestExecution(

        /**
         * Configuration used for this execution
         */
        val conf: PerfTestConfiguration,

        /**
         * Parameters related to the global execution
         */
        val executionParams: MutableMap<String, Any>,

        /**
         * List of result execution times in ms
         */
        val executionResult: MutableList<Long> = mutableListOf(),

        /**
         * Sample execution times in ms, which is used for trend analysis
         */
        val sample: MutableList<Long> = mutableListOf(),

        /**
         * Validation result is set after executions
         */
        var result: PerfTestResult? = null

) {

    /**
     * Holds start time of current execution
     */
    var currentExecutionStart : Long = 0L
        private set

    val executionAvg: Long by lazy {
        Statistics.round(Statistics.getAverage(executionResult))
    }

    val sampleAvg: Long by lazy {
        Statistics.round(Statistics.getAverage(sample))
    }

    val executionMax: Long by lazy { executionResult.max()!! }

    /**
     * Starts new execution, returns start time (nanoseconds)
     */
    fun startNextExecution(): Long {
        currentExecutionStart = System.nanoTime()
        return currentExecutionStart
    }

    fun finishExecution() {
        executionResult.add((System.nanoTime() - currentExecutionStart) / 1000000L)
    }

}

