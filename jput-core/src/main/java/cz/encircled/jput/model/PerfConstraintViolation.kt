package cz.encircled.jput.model

/**
 * Enumeration of possible perf constraint violations
 *
 * @author Vlad on 15-Sep-19.
 */
enum class PerfConstraintViolation(val messageProducer: (execution: PerfTestExecution) -> String) {

    /**
     * Unit test, avg execution time
     */
    UNIT_AVG({
        "Limit avg time = ${it.conf.avgTimeLimit} ms, actual avg time = ${it.executionAvg} ms"
    }),

    /**
     * Unit test, max execution time
     */
    UNIT_MAX({
        "Limit max time = ${it.conf.maxTimeLimit} ms, actual max time = ${it.executionMax} ms"
    }),

    /**
     * Unit test, max execution time
     */
    UNIT_PERCENTILE({
        "Limit ${it.executionParams["percentileRank"]} percentile time = ${it.executionParams["percentileLimit"]} ms, actual percentile time = ${it.executionParams["percentileActual"]} ms"
    }),

    /**
     * Trend test, avg execution time
     */
    TREND_AVG({
        "Limit avg time = ${it.executionParams["avgLimit"]} ms, actual avg time = ${it.executionAvg} ms"
    }),

    EXCEPTIONS_COUNT({
        "Limit exceptions count = ${it.conf.maxAllowedExceptionsCount}, actual = ${it.exceptionsCount()}"
    })

}