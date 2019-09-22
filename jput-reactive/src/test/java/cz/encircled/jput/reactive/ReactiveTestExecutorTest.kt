package cz.encircled.jput.reactive

import cz.encircled.jput.context.JPutContext
import cz.encircled.jput.context.context
import cz.encircled.jput.model.PerfTestConfiguration
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue


/**
 * @author Vlad on 21-Sep-19.
 */
class ReactiveTestExecutorTest {

    private var increment = AtomicInteger(1)

    private val delay = 100L

    private val invocationTimes = mutableMapOf<String, Long>()

    @BeforeTest
    fun before() {
        increment.set(1)

        context = JPutContext()
        context.init()
        context.currentSuite = this::class.java
    }

    @Test(expected = RuntimeException::class)
    fun testRuntimeExceptionRethrown() {
        val executor = ReactiveTestExecutor()

        val conf = PerfTestConfiguration("ReactiveTestExecutorTest#reactiveError", isReactive = true)
        executor.executeTest(conf) { reactiveError() }
    }

    @Test(expected = IllegalStateException::class)
    fun testReactiveBodyNotSet() {
        context = JPutContext()
        context.init()
        val executor = ReactiveTestExecutor()

        val conf = PerfTestConfiguration("ReactiveTestExecutorTest#reactiveBlock", isReactive = true)
        executor.executeTest(conf) {
            Mono.just(1)
        }
    }

    @Test
    fun testMarkingReactiveBodyFromNonJPut() {
        JPutReactive.reactiveTestBody(1.toMono())
    }

    @Test
    fun testMarkingReactiveBodyFromNonJPut2() {
        1.toMono().jputTest()
    }

    /**
     * Test that repeats are split into chunks defined by parallel parameter
     */
    @Test
    fun testReactiveExecutorCorrectChunks() {
        val executor = ReactiveTestExecutor()

        val conf = PerfTestConfiguration("ReactiveTestExecutorTest#reactiveBodyWithDelay", repeats = 4, parallelCount = 2, isReactive = true)
        val executeTest = executor.executeTest(conf, ::reactiveBodyWithDelay)

        // Assert chunk in paralleled
        assertTrue(getDif("1 start", "2 start") < delay / 2)
        assertTrue(getDif("1 end", "2 end") < delay / 2)

        // Assert delay between chunks
        assertTrue(getDif("1 start", "3 start") >= delay)

        // Assert second chunk in paralleled
        assertTrue(getDif("3 start", "4 start") < delay / 2)
        assertTrue(getDif("3 end", "4 end") < delay / 2)

        // Assert that executions are actually run in parallel and haven't wait for others
        assertTrue(executeTest.executionResult.values.all { it.elapsedTime < delay * 2 })
        assertTrue(executeTest.executionResult.values.all { it.elapsedTime >= delay })
    }

    @Test
    fun testReactiveWarmUpExecuted() {
        val executor = ReactiveTestExecutor()

        val conf = PerfTestConfiguration("ReactiveTestExecutorTest#reactiveBodyWithDelay", warmUp = 4, repeats = 1, parallelCount = 3, isReactive = true)
        val executeTest = executor.executeTest(conf, ::reactiveBodyWithDelay)

        // Assert warm up chunk in paralleled (parallel is 3)
        assertTrue(getDif("1 start", "2 start") < delay / 2)
        assertTrue(getDif("1 start", "3 start") < delay / 2)
        assertTrue(getDif("2 start", "3 start") < delay / 2)
        assertTrue(getDif("1 end", "2 end") < delay / 2)
        assertTrue(getDif("1 end", "3 end") < delay / 2)

        // Assert real execution started after warm up
        assertTrue(getDif("3 start", "4 start") >= delay)

        // Assert that executions are actually run in parallel and haven't wait for others
        assertTrue(executeTest.executionResult.values.all { it.elapsedTime < delay * 2 })
        assertTrue(executeTest.executionResult.values.all { it.elapsedTime >= delay })
    }

    private fun getDif(left: String, right: String) =
            (invocationTimes[left]!! - invocationTimes[right]!!).absoluteValue

    private fun reactiveError() {
        Mono.just("1").map {
            throw RuntimeException("Test exception!")
        }.jputTest()
    }

    private fun reactiveBodyWithDelay() {
        Mono.just("")
                .map {
                    val index = increment.getAndIncrement()
                    invocationTimes["$index start"] = System.currentTimeMillis()
                    index
                }
                .delayElement(Duration.ofMillis(delay))
                .map {
                    invocationTimes["$it end"] = System.currentTimeMillis()
                }
                .jputTest()
    }

}