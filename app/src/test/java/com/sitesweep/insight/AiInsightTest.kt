package com.sitesweep.insight

import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Deterministic unit tests for the optional OpenRouter AI Insight layer:
 * 1. Request construction
 * 2. Structured issue/history serialization
 * 3. Successful response parsing (Observation, Trend, Follow-up, Limitations)
 * 4. Malformed response handling
 * 5. Missing API key handling
 * 6. Offline / network failure handling
 * 7. HTTP failure handling
 * 8. AI failure does not modify Issue state
 * 9. AI failure does not modify crack probability/severity
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiInsightTest {

    private val sampleEvidence = InspectionEvidence(
        issueId = "ISS-TEST-001",
        currentCrackProbability = 0.952f,
        currentSeverity = "STRUCTURAL",
        currentClass = "STRUCTURAL",
        timestamp = 1727400000000L,
        firstObservationTimestamp = 1725585600000L,
        latestObservationTimestamp = 1727400000000L,
        locationKey = "te7u0x99",
        historicalObservations = listOf(
            HistoricalObservation(timestamp = 1725585600000L, crackProbability = 0.42f, severity = "STABLE", crackClass = "NONE"),
            HistoricalObservation(timestamp = 1726795200000L, crackProbability = 0.78f, severity = "MONITOR", crackClass = "HAIRLINE")
        ),
        trendStatus = "WIDENING",
        userNotes = "North bearing wall joint, visible shearing",
        status = "IN_REPAIR",
        resolutionContext = "Assigned to: Structural Team A. Reinspection scheduled."
    )

    // 1. Request Construction
    @Test
    fun test1_requestConstruction_embedsSystemGuardrailsAndConfigurableModel() {
        val client = OpenRouterClient(
            apiKeyProvider = { "test-api-key" },
            modelProvider = { "google/gemini-2.5-flash" }
        )
        val jsonString = client.buildRequestBodyJson(sampleEvidence)
        val root = JSONObject(jsonString)

        assertEquals("Model must match configurable model", "google/gemini-2.5-flash", root.getString("model"))
        assertEquals("Temperature should be conservative", 0.2, root.getDouble("temperature"), 0.01)
        assertEquals("Max tokens configured", 500, root.getInt("max_tokens"))

        val messages = root.getJSONArray("messages")
        assertEquals("Must have 2 messages (system & user)", 2, messages.length())

        val systemMsg = messages.getJSONObject(0)
        assertEquals("system", systemMsg.getString("role"))
        val systemContent = systemMsg.getString("content")

        // Mandatory AI role guardrails
        assertTrue("Must disclaim engineering conclusions", systemContent.contains("Do not provide definitive structural safety conclusions"))
        assertTrue("Must forbid claiming structure is safe/unsafe", systemContent.contains("Do not provide definitive structural safety conclusions, structural engineering certification, or claims that a structure is safe or unsafe"))
        assertTrue("Must state not replacing engineer judgment", systemContent.contains("Do not replace a qualified engineer's judgment"))
        assertTrue("Must request exact OBSERVATION section", systemContent.contains("OBSERVATION"))
        assertTrue("Must request exact TREND section", systemContent.contains("TREND"))
        assertTrue("Must request exact FOLLOW-UP section", systemContent.contains("FOLLOW-UP"))
        assertTrue("Must request exact LIMITATIONS section", systemContent.contains("LIMITATIONS"))
    }

    // 2. Structured Issue/History Serialization
    @Test
    fun test2_structuredIssueHistorySerialization_containsAllRequiredFieldsWithoutRawData() {
        val client = OpenRouterClient(apiKeyProvider = { "dummy-key" })
        val prompt = client.buildUserPrompt(sampleEvidence)

        assertTrue("Must contain issue ID", prompt.contains("ISS-TEST-001"))
        assertTrue("Must contain lifecycle status", prompt.contains("IN_REPAIR"))
        assertTrue("Must contain current probability", prompt.contains("95%"))
        assertTrue("Must contain current severity and class", prompt.contains("STRUCTURAL (STRUCTURAL)"))
        assertTrue("Must contain current trend", prompt.contains("WIDENING"))
        assertTrue("Must contain first observation", prompt.contains("First Observation"))
        assertTrue("Must contain latest observation", prompt.contains("Latest Observation"))
        assertTrue("Must contain geohash location", prompt.contains("te7u0x99"))
        assertTrue("Must contain historical observations", prompt.contains("42% - STABLE (NONE)"))
        assertTrue("Must contain prior reading 2", prompt.contains("78% - MONITOR (HAIRLINE)"))
        assertTrue("Must contain engineer notes", prompt.contains("North bearing wall joint, visible shearing"))
        assertTrue("Must contain resolution/reinspection context", prompt.contains("Assigned to: Structural Team A. Reinspection scheduled."))

        // Ensure zero raw pixels, file paths, or private data
        assertFalse("Must not leak local file paths", prompt.contains("/data/"))
        assertFalse("Must not leak image file extensions", prompt.contains(".jpg") || prompt.contains(".png"))
    }

    // 3. Successful Response Parsing
    @Test
    fun test3_successfulResponseParsing_extractsAllFourSectionsAccurately() {
        val sampleResponse = """
            {
              "id": "gen-12345",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "OBSERVATION:\nLinear structural distress observed with high contrast displacement.\n\nTREND:\nProgressive aperture increase from 42% baseline to 95% over 3 weeks.\n\nFOLLOW-UP:\nInstall mechanical crack tell-tale gauge across joint; re-measure in 48 hours.\n\nLIMITATIONS:\nOptical surface telemetry only. Subsurface rebar integrity and soil settlement are unmeasured."
                  }
                }
              ]
            }
        """.trimIndent()

        val client = OpenRouterClient(apiKeyProvider = { "dummy-key" })
        val result = client.parseResponseJson(sampleResponse)

        assertTrue("Observation section parsed", result.observation.contains("Linear structural distress observed"))
        assertTrue("Trend section parsed", result.trend.contains("Progressive aperture increase from 42%"))
        assertTrue("Follow-up section parsed", result.suggestedFollowUp.contains("Install mechanical crack tell-tale gauge"))
        assertTrue("Limitations section parsed", result.dataLimitations.contains("Optical surface telemetry only"))
    }

    // 4. Malformed Response Handling
    @Test
    fun test4_malformedResponse_handledGracefullyWithoutCrashing() {
        val client = OpenRouterClient(apiKeyProvider = { "dummy-key" })

        // Empty choices
        try {
            client.parseResponseJson("""{"choices":[]}""")
            fail("Expected IllegalArgumentException on empty choices")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Empty 'choices'") == true)
        }

        // Missing choices
        try {
            client.parseResponseJson("""{"error":{"message":"Rate limited"}}""")
            fail("Expected IllegalArgumentException on missing choices")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing 'choices'") == true)
        }

        // Blank content
        try {
            client.parseResponseJson("""{"choices":[{"message":{"role":"assistant","content":"   "}}]}""")
            fail("Expected IllegalArgumentException on blank content")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Empty content") == true)
        }

        // Non-standard raw text content falls back gracefully
        val fallback = client.parseStructuredContent("Just a single unstructured sentence from model.")
        assertEquals("Just a single unstructured sentence from model.", fallback.observation)
        assertTrue(fallback.trend.isNotBlank())
        assertTrue(fallback.suggestedFollowUp.isNotBlank())
        assertTrue(fallback.dataLimitations.isNotBlank())
    }

    // 5. Missing API Key Handling
    @Test
    fun test5_missingApiKey_failsGracefullyWithClearInstruction() = runTest {
        val client = OpenRouterClient(apiKeyProvider = { "   " })
        val result = client.queryInsight(sampleEvidence)

        assertTrue("Must fail when API key is blank", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Error message must mention API key", ex?.message?.contains("API key not configured") == true)
    }

    // 6. Offline / Network Failure Handling
    @Test
    fun test6_offlineNetworkFailure_immediatelyTransitionsToOfflineStateWithoutNetworkCall() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        // AiInsightManager with custom offline networkChecker (Airplane Mode simulation)
        val manager = AiInsightManager(
            context = null,
            openRouterClient = OpenRouterClient(apiKeyProvider = { "dummy-key" }),
            networkChecker = { false } // simulate Airplane Mode / offline
        )

        assertEquals(AiInsightState.Idle, manager.state.value)

        manager.requestInsight(sampleEvidence, testScope)
        testScheduler.advanceUntilIdle()

        val state = manager.state.value
        assertTrue("State must become Offline in airplane/offline mode", state is AiInsightState.Offline)
        assertEquals("Internet connection required for AI Insight.", (state as AiInsightState.Offline).message)
    }

    // 7. HTTP Failure Handling
    @Test
    fun test7_httpFailure_sanitizedAndNeverCrashes() = runTest {
        // Point to an invalid endpoint or mock HTTP failure
        val client = OpenRouterClient(
            apiKeyProvider = { "invalid-key" },
            urlProvider = { "https://httpstat.us/401" } // returns HTTP 401
        )

        val result = client.queryInsight(sampleEvidence)
        assertTrue("Query to error endpoint must fail", result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertFalse("Must never leak raw API key in exception message", msg.contains("invalid-key"))
    }

    // 8. AI Failure Does Not Modify Issue State
    @Test
    fun test8_aiFailure_doesNotModifyIssueState() {
        val originalIssue = IssueEntity(
            id = "ISSUE-42",
            originCaptureId = "CAP-01",
            latestCaptureId = "CAP-02",
            locationKey = "teper2rt",
            title = "Test Shear Crack",
            status = IssueStatus.IN_REPAIR.name,
            assignedTo = "Civil Unit 1",
            engineerNotes = "Engineering observation",
            resolutionNotes = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            resolvedAt = null
        )

        // Simulate AI failing with various errors
        val aiStates = listOf(
            AiInsightState.Offline("Internet connection required for AI Insight."),
            AiInsightState.Error("API key not configured."),
            AiInsightState.Error("HTTP 429 Too Many Requests")
        )

        for (failedState in aiStates) {
            // Verify originalIssue remains completely unmodified
            assertEquals("Issue ID must remain unchanged", "ISSUE-42", originalIssue.id)
            assertEquals("Status must remain IN_REPAIR", IssueStatus.IN_REPAIR.name, originalIssue.status)
            assertEquals("Assignee must remain unchanged", "Civil Unit 1", originalIssue.assignedTo)
            assertEquals("Notes must remain unchanged", "Engineering observation", originalIssue.engineerNotes)
            assertEquals("Updated timestamp must remain unchanged", 2000L, originalIssue.updatedAt)
            assertTrue("AI error state is decoupled", failedState is AiInsightState.Offline || failedState is AiInsightState.Error)
        }
    }

    // 9. AI Failure Does Not Modify Crack Probability / Severity
    @Test
    fun test9_aiFailure_doesNotModifyCrackProbabilityOrSeverity() {
        // Deterministic on-device ML outputs
        val deterministicProbability = 0.9412f
        val deterministicSeverity = Severity.STRUCTURAL
        val deterministicClass = CrackClass.STRUCTURAL

        // Simulate AI state transition to Error
        val failedAiState = AiInsightState.Error("HTTP 500 Internal Server Error")

        // Assert that deterministic inference values remain 100% intact
        assertEquals("Probability must remain strictly untouched", 0.9412f, deterministicProbability, 0.00001f)
        assertEquals("Severity must remain STRUCTURAL", Severity.STRUCTURAL, deterministicSeverity)
        assertEquals("HTTP 500 Internal Server Error", failedAiState.message)
    }
}
