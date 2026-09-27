package com.sitesweep.insight

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates optional AI Insight execution:
 * 1. Pre-flight network availability check (fast fail in Airplane Mode without opening sockets).
 * 2. Background query to OpenRouter using structured evidence only.
 * 3. Never interferes with or mutates deterministic camera detection state or Issue status.
 */
class AiInsightManager(
    private val context: Context? = null,
    val openRouterClient: OpenRouterClient = OpenRouterClient(),
    private val networkChecker: ((Context?) -> Boolean)? = null
) {

    companion object {
        private const val TAG = "AiInsightManager"

        /**
         * Checks if the device has an active internet connection.
         * In Airplane Mode or offline environments, returns false instantaneously.
         */
        fun isNetworkAvailable(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (e: Exception) {
                Log.w(TAG, "Network capability check exception: ${e.message}")
                false
            }
        }
    }

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Idle)
    val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private var requestJob: Job? = null

    /**
     * Resets state back to Idle and cancels ongoing request if any.
     */
    fun reset() {
        requestJob?.cancel()
        requestJob = null
        _state.value = AiInsightState.Idle
    }

    /**
     * User-triggered request for engineering AI insight.
     * Guaranteed to never crash or run autonomously.
     */
    fun requestInsight(evidence: InspectionEvidence, scope: CoroutineScope) {
        requestJob?.cancel()

        requestJob = scope.launch {
            _state.value = AiInsightState.CheckingConnection

            val online = networkChecker?.invoke(context) ?: (context?.let { isNetworkAvailable(it) } ?: false)
            if (!online) {
                _state.value = AiInsightState.Offline("Internet connection required for AI Insight.")
                return@launch
            }

            _state.value = AiInsightState.Analyzing

            val result = openRouterClient.queryInsight(evidence)
            result.fold(
                onSuccess = { insight ->
                    _state.value = AiInsightState.Success(insight)
                },
                onFailure = { error ->
                    _state.value = AiInsightState.Error(error.message ?: "Failed to generate AI insight.")
                }
            )
        }
    }
}
