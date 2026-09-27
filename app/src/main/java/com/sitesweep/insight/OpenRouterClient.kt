package com.sitesweep.insight

import android.util.Log
import com.sitesweep.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Lightweight, zero-dependency client querying OpenRouter for advisory engineering insights.
 * Never streams camera frames or images. Operates strictly on discrete structured evidence.
 * API key is safely injected via BuildConfig from local.properties/environment (not committed to git).
 */
class OpenRouterClient(
    private val apiKeyProvider: () -> String = { BuildConfig.OPENROUTER_API_KEY },
    private val modelProvider: () -> String = { BuildConfig.OPENROUTER_MODEL },
    private val urlProvider: () -> String = { OPENROUTER_ENDPOINT }
) {

    companion object {
        private const val TAG = "OpenRouterClient"
        const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000

        const val SYSTEM_PROMPT = """You are an inspection-assistance system. Analyze the supplied inspection evidence and summarize observable indicators and changes over time. Do not provide definitive structural safety conclusions, structural engineering certification, or claims that a structure is safe or unsafe. Do not replace a qualified engineer's judgment.

Return exactly:
OBSERVATION
What the inspection evidence indicates.

TREND
How the evidence has changed compared with previous observations.

FOLLOW-UP
A reasonable inspection/follow-up action based on the evidence.

LIMITATIONS
What cannot be concluded from the supplied data.

Keep the response concise and phone-readable."""
    }

    /**
     * Constructs the structured prompt for OpenRouter without exposing personal or raw image data.
     */
    fun buildUserPrompt(evidence: InspectionEvidence): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()

        sb.appendLine("STRUCTURED INSPECTION EVIDENCE:")
        if (!evidence.issueId.isNullOrBlank()) {
            sb.appendLine("- Issue ID: ${evidence.issueId}")
        }
        if (!evidence.status.isNullOrBlank()) {
            sb.appendLine("- Issue Lifecycle Status: ${evidence.status}")
        }
        sb.appendLine("- Current Probability: ${kotlin.math.round(evidence.currentCrackProbability * 100f).toInt()}%")
        sb.appendLine("- Current Severity: ${evidence.currentSeverity} (${evidence.currentClass})")
        if (!evidence.trendStatus.isNullOrBlank()) {
            sb.appendLine("- Current Trend: ${evidence.trendStatus}")
        }
        if (evidence.firstObservationTimestamp != null) {
            sb.appendLine("- First Observation: ${dateFormat.format(Date(evidence.firstObservationTimestamp))} UTC")
        }
        if (evidence.latestObservationTimestamp != null) {
            sb.appendLine("- Latest Observation: ${dateFormat.format(Date(evidence.latestObservationTimestamp))} UTC")
        } else {
            sb.appendLine("- Timestamp: ${dateFormat.format(Date(evidence.timestamp))} UTC")
        }
        if (!evidence.locationKey.isNullOrBlank()) {
            sb.appendLine("- Location Key (Geohash): ${evidence.locationKey}")
        }

        if (evidence.historicalObservations.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("HISTORICAL OBSERVATIONS (${evidence.historicalObservations.size} prior readings):")
            for ((idx, hist) in evidence.historicalObservations.withIndex()) {
                val dateStr = dateFormat.format(Date(hist.timestamp))
                val pct = kotlin.math.round(hist.crackProbability * 100f).toInt()
                val cls = hist.crackClass?.let { " ($it)" } ?: ""
                sb.appendLine("  ${idx + 1}. $dateStr: $pct% - ${hist.severity}$cls")
            }
        } else {
            sb.appendLine("- Prior Observations: Baseline observation (no prior captures recorded).")
        }

        if (!evidence.userNotes.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("ENGINEER OBSERVATION NOTES: ${evidence.userNotes}")
        }

        if (!evidence.resolutionContext.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("REINSPECTION / RESOLUTION CONTEXT: ${evidence.resolutionContext}")
        }

        return sb.toString().trim()
    }

    /**
     * Builds the JSON request payload for OpenRouter.
     */
    fun buildRequestBodyJson(evidence: InspectionEvidence, model: String = modelProvider()): String {
        val root = JSONObject()
        root.put("model", model.ifBlank { "google/gemini-2.5-flash" })
        root.put("temperature", 0.2)
        root.put("max_tokens", 500)

        val messages = JSONArray()

        val systemMsg = JSONObject()
        systemMsg.put("role", "system")
        systemMsg.put("content", SYSTEM_PROMPT)
        messages.put(systemMsg)

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", buildUserPrompt(evidence))
        messages.put(userMsg)

        root.put("messages", messages)
        return root.toString()
    }

    /**
     * Parses the OpenRouter response JSON into structured insight sections.
     */
    fun parseResponseJson(jsonString: String): AiInsightResult {
        val root = JSONObject(jsonString)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalArgumentException("Missing 'choices' array in response")

        if (choices.length() == 0) {
            throw IllegalArgumentException("Empty 'choices' in OpenRouter response")
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message")
            ?: throw IllegalArgumentException("Missing 'message' in choice")

        val content = message.optString("content", "").trim()
        if (content.isBlank()) {
            throw IllegalArgumentException("Empty content received from OpenRouter")
        }

        return parseStructuredContent(content)
    }

    /**
     * Segregates raw text content into the 4 structured advisory sections:
     * OBSERVATION, TREND, FOLLOW-UP, LIMITATIONS.
     */
    fun parseStructuredContent(content: String): AiInsightResult {
        val lines = content.lines()
        var currentSection = 0 // 1: Obs, 2: Trend, 3: FollowUp, 4: Limitations
        val obsLines = mutableListOf<String>()
        val trendLines = mutableListOf<String>()
        val followUpLines = mutableListOf<String>()
        val limitationLines = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            val clean = line.replace("*", "").replace("#", "").trim()
            val lower = clean.lowercase(Locale.US)

            when {
                lower.startsWith("observation") || (lower.startsWith("1.") && lower.contains("observation")) -> {
                    currentSection = 1
                    val rest = clean.substringAfter(":").trim()
                    if (rest.isNotBlank() && !rest.equals(clean, ignoreCase = true)) {
                        obsLines.add(rest)
                    }
                }
                lower.startsWith("trend") || (lower.startsWith("2.") && lower.contains("trend")) -> {
                    currentSection = 2
                    val rest = clean.substringAfter(":").trim()
                    if (rest.isNotBlank() && !rest.equals(clean, ignoreCase = true)) {
                        trendLines.add(rest)
                    }
                }
                lower.startsWith("follow-up") || lower.startsWith("follow up") || (lower.startsWith("3.") && lower.contains("follow")) -> {
                    currentSection = 3
                    val rest = clean.substringAfter(":").trim()
                    if (rest.isNotBlank() && !rest.equals(clean, ignoreCase = true)) {
                        followUpLines.add(rest)
                    }
                }
                lower.startsWith("limitation") || (lower.startsWith("4.") && lower.contains("limitation")) -> {
                    currentSection = 4
                    val rest = clean.substringAfter(":").trim()
                    if (rest.isNotBlank() && !rest.equals(clean, ignoreCase = true)) {
                        limitationLines.add(rest)
                    }
                }
                else -> {
                    when (currentSection) {
                        1 -> if (line.isNotBlank()) obsLines.add(line)
                        2 -> if (line.isNotBlank()) trendLines.add(line)
                        3 -> if (line.isNotBlank()) followUpLines.add(line)
                        4 -> if (line.isNotBlank()) limitationLines.add(line)
                    }
                }
            }
        }

        val observation = obsLines.joinToString("\n").trim()
        val trend = trendLines.joinToString("\n").trim()
        val followUp = followUpLines.joinToString("\n").trim()
        val limitations = limitationLines.joinToString("\n").trim()

        return AiInsightResult(
            observation = observation.ifBlank {
                if (content.isNotBlank() && trend.isBlank() && followUp.isBlank()) content
                else "Distress indicators observed at recorded probability."
            },
            trend = trend.ifBlank { "Baseline observation; no progressive widening confirmed." },
            suggestedFollowUp = followUp.ifBlank { "Perform non-destructive physical measurement with crack gauge." },
            dataLimitations = limitations.ifBlank { "Advisory optical telemetry only; does not replace qualified engineer certification." },
            rawResponse = content
        )
    }

    /**
     * Executes the HTTP request on Dispatchers.IO.
     * Guaranteed to never throw unhandled exceptions or crash.
     * Sanitizes errors so API keys are NEVER exposed in messages or logs.
     */
    suspend fun queryInsight(evidence: InspectionEvidence): Result<AiInsightResult> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("OpenRouter API key not configured. Add OPENROUTER_API_KEY to local.properties or .env")
            )
        }

        val model = modelProvider().ifBlank { "google/gemini-2.5-flash" }
        var connection: HttpsURLConnection? = null

        try {
            val url = URL(urlProvider())
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("HTTP-Referer", "https://sitesweep.app")
                setRequestProperty("X-Title", "SiteSweep Hackathon")
            }

            val requestBody = buildRequestBodyJson(evidence, model)

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                val parsed = parseResponseJson(responseText)
                Result.success(parsed)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorText = try {
                    BufferedReader(InputStreamReader(errorStream, "UTF-8")).use { it.readText() }
                } catch (e: Exception) {
                    "HTTP $responseCode"
                }
                // Do not log raw authorization header or sensitive inspection data
                Log.w(TAG, "OpenRouter error HTTP $responseCode")
                val sanitizedMessage = when (responseCode) {
                    401 -> "Invalid or unauthorized API key."
                    429 -> "Rate limit exceeded on OpenRouter. Please try again shortly."
                    500, 502, 503 -> "OpenRouter service temporarily unavailable (HTTP $responseCode)."
                    else -> "OpenRouter returned HTTP $responseCode."
                }
                Result.failure(RuntimeException(sanitizedMessage))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(RuntimeException("Connection timed out reaching OpenRouter."))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(RuntimeException("Unable to resolve OpenRouter. Check internet connectivity."))
        } catch (e: Exception) {
            Log.w(TAG, "OpenRouter query failure: ${e.javaClass.simpleName}")
            Result.failure(RuntimeException(e.message ?: "Failed to query AI insight."))
        } finally {
            connection?.disconnect()
        }
    }
}
