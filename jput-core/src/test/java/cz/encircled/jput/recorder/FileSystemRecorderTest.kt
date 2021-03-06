package cz.encircled.jput.recorder

import cz.encircled.jput.ShortcutsForTests
import cz.encircled.jput.context.JPutContext
import cz.encircled.jput.model.TrendTestConfiguration
import cz.encircled.jput.runner.junit.JPutJUnit4Runner
import cz.encircled.jput.trend.SelectionStrategy
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author Vlad on 21-May-17.
 */
@RunWith(JPutJUnit4Runner::class)
class FileSystemRecorderTest : ShortcutsForTests {

    @Test
    fun testWriteResults_FSResultRecorder() {
        val (pathToFile, writer) = getWriter()

        val config = configWithTrend(TrendTestConfiguration(100, sampleSelectionStrategy = SelectionStrategy.USE_FIRST))
        val configUseLast = configWithTrend(TrendTestConfiguration(4, sampleSelectionStrategy = SelectionStrategy.USE_LATEST))
        val configWithSampleLimit = configWithTrend(TrendTestConfiguration(4, sampleSelectionStrategy = SelectionStrategy.USE_FIRST))

        val run = getTestExecution(config, 100, 110, 120, 90)
        val runWithSampleLimit = getTestExecution(configWithSampleLimit, 100, 110, 120, 95)

        writer.appendTrendResult(run)
        writer.appendTrendResult(getTestExecution(baseConfig(), 130, 115, 105, 100))
        writer.flush()

        // Read previously written data

        val reader = FileSystemResultRecorder(pathToFile)
        var runs = reader.getSample(run)
        assertEquals(listOf<Long>(90, 100, 100, 105, 110, 115, 120, 130), runs.sorted())

        runs = reader.getSample(runWithSampleLimit)
        assertEquals(listOf<Long>(90, 100, 110, 120), runs.sorted())

        runs = reader.getSample(getTestExecution(configUseLast))
        assertEquals(listOf<Long>(100, 105, 115, 130), runs.sorted())
    }

    @Test
    fun testSampleStrategy() {
        val (_, writer) = getWriter()

        assertEquals(listOf(1, 2), writer.subList(listOf(1, 2, 3, 4), 2, SelectionStrategy.USE_FIRST))
        assertEquals(listOf(3, 4), writer.subList(listOf(1, 2, 3, 4), 2, SelectionStrategy.USE_LATEST))
    }

    @Test
    fun testUserDefinedEnvParams() = testWithProps(JPutContext.PROP_ENV_PARAMS to "test1:1,test2:abc") {
        val (_, writer) = getWriter()

        assertEquals(mapOf(
                "test1" to "1",
                "test2" to "abc"
        ), writer.getUserDefinedEnvParams())
    }

    @Test
    fun testFileCreatedIfNotExist() {
        val temp = File.createTempFile("jput-test", "")
        val pathToFile = temp.path + "_not_exist"
        assertFalse(File(pathToFile).exists())

        FileSystemResultRecorder(pathToFile)
        assertTrue(File(pathToFile).exists())
    }

    private fun getWriter(): Pair<String, FileSystemResultRecorder> {
        val temp = File.createTempFile("jput-test", "")

        val writer = FileSystemResultRecorder(temp.path)
        return Pair(temp.path, writer)
    }

}
