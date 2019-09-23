package cz.encircled.jput.recorder

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.matching.RegexPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.UrlPattern
import cz.encircled.jput.ShortcutsForTests
import cz.encircled.jput.context.JPutContext
import cz.encircled.jput.context.context
import cz.encircled.jput.model.ExecutionRepeat
import cz.encircled.jput.model.TrendTestConfiguration
import cz.encircled.jput.runner.JPutJUnit4Runner
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.joda.time.DateTime
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Vlad on 15-Sep-19.
 */
@RunWith(JPutJUnit4Runner::class)
open class ElasticsearchRecorderTest : ShortcutsForTests {

    @AfterTest
    fun after() {
        wireMockServer.resetRequests()
    }

    @Test
    fun testGetSample() {
        val client = ElasticsearchClientWrapper(RestClient.builder(HttpHost("localhost", port, "http")))
        val ecs = ElasticsearchResultRecorder(client)

        val execution = getTestExecution(configWithTrend(TrendTestConfiguration(
                sampleSize = 5
        )))
        assertEquals(listOf(95L, 105L), ecs.getSample(execution))

        wireMockServer.verify(1, RequestPatternBuilder(RequestMethod.POST, UrlPattern(RegexPattern("/jput/_search.*"), true)))
    }

    @Test
    fun testGetSampleWhenIndexNotExist() = testWithProps(JPutContext.PROP_ELASTIC_INDEX to "new") {
        val client = ElasticsearchClientWrapper(RestClient.builder(HttpHost("localhost", port, "http")))
        val ecs = ElasticsearchResultRecorder(client)

        val execution = getTestExecution(configWithTrend(TrendTestConfiguration(
                sampleSize = 5
        )))
        assertTrue(ecs.getSample(execution).isEmpty())

        wireMockServer.verify(1, RequestPatternBuilder(RequestMethod.POST, UrlPattern(RegexPattern("/new/_search.*"), true)))
    }

    @Test
    fun testAddingEntries() {
        val client = ElasticsearchClientWrapper(RestClient.builder(HttpHost("localhost", port, "http")))
        val ecs = ElasticsearchResultRecorder(client)

        val execution = getTestExecution(configWithTrend(TrendTestConfiguration(
                sampleSize = 5
        )))

        val now = DateTime.now()

        execution.executionResult[1] = ExecutionRepeat(execution, 0L, 321L, now)
        execution.executionResult[2] = ExecutionRepeat(execution, 0L, 4321L, now)
        ecs.appendTrendResult(execution)
        ecs.flush()

        // TODO test user params

        val expected = "{\"index\":{\"_index\":\"jput\",\"_type\":\"jput\"}}\n" +
                "{\"executionId\":\"${context.executionId}\",\"testId\":\"1\",\"start\":\"$now\",\"elapsed\":321}\n" +
                "{\"index\":{\"_index\":\"jput\",\"_type\":\"jput\"}}\n" +
                "{\"executionId\":\"${context.executionId}\",\"testId\":\"1\",\"start\":\"$now\",\"elapsed\":4321}\n"

        wireMockServer.verify(postRequestedFor(urlEqualTo("/_bulk?timeout=1m"))
                .withRequestBody(equalTo(expected)))
    }

    @Test
    fun testDestroy() {
        val client = ElasticsearchClientWrapper(RestClient.builder(HttpHost("not_exist", port, "http")))
        val ecs = ElasticsearchResultRecorder(client)
        // Should not fail
        ecs.destroy()
    }

    companion object {

        private const val port = 9200

        private var wireMockServer = WireMockServer(WireMockConfiguration()
                .extensions(ResponseTemplateTransformer(true))
                .port(port))

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            wireMockServer.start()
            configureFor(port)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            wireMockServer.stop()
        }

    }

}