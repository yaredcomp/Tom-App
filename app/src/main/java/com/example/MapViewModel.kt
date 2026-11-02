package com.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface RouteUiState {
    data object Idle : RouteUiState
    data object Loading : RouteUiState
    data class Success(
        val distanceFormatted: String,
        val durationFormatted: String,
        val geometry: List<GeoPoint>,
        val destinationPoint: GeoPoint
    ) : RouteUiState
    data class Error(val message: String) : RouteUiState
}

data class DestinationPreset(
    val title: String,
    val latitude: Double,
    val longitude: Double
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MapViewModel"
        // Default initial coordinates centered in Amsterdam per user choice
        val DEFAULT_AMSTERDAM_LOCATION = GeoPoint(52.37616, 4.90828)
    }

    private val _currentLocation = MutableStateFlow<GeoPoint?>(DEFAULT_AMSTERDAM_LOCATION)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _destinationLat = MutableStateFlow("52.3086")
    val destinationLat: StateFlow<String> = _destinationLat.asStateFlow()

    private val _destinationLng = MutableStateFlow("4.7639")
    val destinationLng: StateFlow<String> = _destinationLng.asStateFlow()

    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState.asStateFlow()

    private val _apiKeyMissing = MutableStateFlow(false)
    val apiKeyMissing: StateFlow<Boolean> = _apiKeyMissing.asStateFlow()

    val presets = listOf(
        DestinationPreset("Schiphol Airport", 52.3086, 4.7639),
        DestinationPreset("Dam Square", 52.3731, 4.8936),
        DestinationPreset("Rotterdam Central", 51.9244, 4.4777),
        DestinationPreset("Utrecht Dom", 52.0907, 5.1214)
    )

    private var routePlannerInstance: RoutePlanner? = null

    init {
        val apiKey = BuildConfig.TOMTOM_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_TOMTOM_API_KEY") {
            _apiKeyMissing.value = true
            Log.w(TAG, "TomTom API key is not configured in BuildConfig.")
        }
        observeLocationProvider()
    }

    private fun observeLocationProvider() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!TomTomSdk.isInitialized) {
                    val key = BuildConfig.TOMTOM_API_KEY.ifEmpty { "PLACEHOLDER_KEY" }
                    val sdkConfig = buildSdkConfiguration(
                        context = getApplication(),
                        apiKey = key
                    )
                    TomTomSdk.initialize(getApplication(), sdkConfig)
                }

                TomTomSdk.locationProvider.addOnLocationUpdateListener(object : OnLocationUpdateListener {
                    override fun onLocationUpdate(location: GeoLocation) {
                        val point = location.position
                        _currentLocation.value = point
                        Log.d(TAG, "Received GPS location: ${point.latitude}, ${point.longitude}")
                    }
                })
                TomTomSdk.locationProvider.enable()
            } catch (e: Exception) {
                Log.w(TAG, "Could not attach to TomTom location provider: ${e.message}")
            }
        }
    }

    fun onDestinationLatChanged(value: String) {
        _destinationLat.value = value
    }

    fun onDestinationLngChanged(value: String) {
        _destinationLng.value = value
    }

    fun applyPreset(preset: DestinationPreset) {
        _destinationLat.value = preset.latitude.toString()
        _destinationLng.value = preset.longitude.toString()
    }

    fun updateCurrentLocation(point: GeoPoint) {
        _currentLocation.value = point
    }

    fun dismissError() {
        if (_routeState.value is RouteUiState.Error) {
            _routeState.value = RouteUiState.Idle
        }
    }

    fun planRoute(userLocationFromMap: GeoPoint? = null) {
        val origin = userLocationFromMap ?: _currentLocation.value ?: DEFAULT_AMSTERDAM_LOCATION
        val latStr = _destinationLat.value.trim()
        val lngStr = _destinationLng.value.trim()

        val destLat = latStr.toDoubleOrNull()
        val destLng = lngStr.toDoubleOrNull()

        if (destLat == null || destLng == null) {
            _routeState.value = RouteUiState.Error("Please enter valid numeric latitude and longitude coordinates.")
            return
        }

        if (destLat < -90.0 || destLat > 90.0 || destLng < -180.0 || destLng > 180.0) {
            _routeState.value = RouteUiState.Error("Latitude must be between -90 and 90, and longitude between -180 and 180.")
            return
        }

        val destinationPoint = GeoPoint(destLat, destLng)

        val apiKey = BuildConfig.TOMTOM_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_TOMTOM_API_KEY") {
            _apiKeyMissing.value = true
            _routeState.value = RouteUiState.Error("TomTom API key not detected. Please configure TOMTOM_API_KEY in the AI Studio Secrets panel.")
            return
        }

        _routeState.value = RouteUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val planner = routePlannerInstance ?: OnlineRoutePlanner.create(
                    context = getApplication(),
                    apiKey = apiKey
                ).also { routePlannerInstance = it }

                val itinerary = Itinerary(origin = origin, destination = destinationPoint)
                val routePlanningOptions = RoutePlanningOptions(itinerary = itinerary)

                planner.planRoute(routePlanningOptions, object : RoutePlanningCallback {
                    override fun onSuccess(result: RoutePlanningResponse) {
                        val routes = result.routes
                        if (routes.isEmpty()) {
                            _routeState.value = RouteUiState.Error("No route found to the specified coordinates.")
                            return
                        }

                        val firstRoute: Route = routes.first()
                        val geometry = firstRoute.geometry
                        val summary = firstRoute.summary

                        val distanceMeters = summary.length.inMeters()
                        val distanceKm = distanceMeters / 1000.0
                        val distanceFormatted = if (distanceKm >= 1.0) {
                            String.format(Locale.getDefault(), "%.1f km", distanceKm)
                        } else {
                            String.format(Locale.getDefault(), "%d m", distanceMeters.toInt())
                        }

                        val travelTimeSeconds = summary.travelTime.inWholeSeconds
                        val minutes = travelTimeSeconds / 60
                        val durationFormatted = if (minutes >= 60) {
                            val hours = minutes / 60
                            val remMins = minutes % 60
                            "${hours}h ${remMins}m"
                        } else {
                            "${minutes.coerceAtLeast(1)} min"
                        }

                        Log.d(TAG, "Route planned: $distanceFormatted, $durationFormatted, ${geometry.size} points")

                        _routeState.value = RouteUiState.Success(
                            distanceFormatted = distanceFormatted,
                            durationFormatted = durationFormatted,
                            geometry = geometry,
                            destinationPoint = destinationPoint
                        )
                    }

                    override fun onFailure(failure: RoutingFailure) {
                        val errorMsg = failure.message.ifBlank { "Failed to calculate route. Check your coordinates and network connection." }
                        Log.e(TAG, "Routing failure: $errorMsg")
                        _routeState.value = RouteUiState.Error(errorMsg)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception during route planning", e)
                withContext(Dispatchers.Main) {
                    _routeState.value = RouteUiState.Error(e.localizedMessage ?: "Unexpected error during route planning.")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            TomTomSdk.locationProvider.disable()
            routePlannerInstance?.close()
        } catch (ignored: Exception) {}
    }
}
