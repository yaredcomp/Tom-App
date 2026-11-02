package com.example

import androidx.compose.animation.AnimatedVisibility
import com.tomtom.sdk.map.display.compose.ExperimentalMapComposableApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CardBorderDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SurfaceGlassDark
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapLocationInfrastructure
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.compose.TomTomMap
import com.tomtom.sdk.map.display.compose.model.MapDisplayInfrastructure
import com.tomtom.sdk.map.display.compose.nodes.CurrentLocationMarker
import com.tomtom.sdk.map.display.compose.nodes.Route
import com.tomtom.sdk.map.display.compose.state.rememberMapViewState
import com.tomtom.sdk.map.display.style.StandardStyles
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMapComposableApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val destLat by viewModel.destinationLat.collectAsStateWithLifecycle()
    val destLng by viewModel.destinationLng.collectAsStateWithLifecycle()
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()
    val apiKeyMissing by viewModel.apiKeyMissing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val initialPosition = remember {
        currentLocation ?: MapViewModel.DEFAULT_AMSTERDAM_LOCATION
    }

    val mapDisplayInfrastructure = remember {
        MapDisplayInfrastructure(TomTomSdk.sdkContext) {
            locationInfrastructure = MapLocationInfrastructure {
                locationProvider = TomTomSdk.locationProvider
            }
        }
    }

    val mapViewState = rememberMapViewState(
        initialCameraOptions = InitialCameraOptions.LocationBased(
            position = initialPosition,
            zoom = 13.0
        ),
        initialStyle = StandardStyles.SATELLITE
    )

    // Handle route results: automatically zoom camera to show calculated route
    LaunchedEffect(routeState) {
        when (val state = routeState) {
            is RouteUiState.Success -> {
                mapViewState.cameraState.zoomToAllRoutes()
                snackbarHostState.showSnackbar("Route planned: ${state.distanceFormatted} (${state.durationFormatted})")
            }
            is RouteUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Satellite Map View (Fills background)
            TomTomMap(
                infrastructure = mapDisplayInfrastructure,
                state = mapViewState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tomtom_map_view")
            ) {
                // Display current user location indicator
                CurrentLocationMarker()

                // Display planned route polyline
                if (routeState is RouteUiState.Success) {
                    val success = routeState as RouteUiState.Success
                    Route(
                        geometry = success.geometry
                    )
                }
            }

            // Top Status Overlay (Coordinates & Satellite Tag)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // API Key Missing Warning Banner
                AnimatedVisibility(
                    visible = apiKeyMissing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xD9B71C1C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "TomTom API Key Missing: Set TOMTOM_API_KEY in the AI Studio Secrets panel.",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // GPS & Satellite Badge Pill
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceGlassDark),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(20.dp))
                        .testTag("location_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00B0FF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SATELLITE",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Current GPS",
                            tint = EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )

                        val currentPos = currentLocation ?: MapViewModel.DEFAULT_AMSTERDAM_LOCATION
                        Text(
                            text = String.format(
                                Locale.US,
                                "Lat: %.5f | Lng: %.5f",
                                currentPos.latitude,
                                currentPos.longitude
                            ),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Recenter Floating Action Button (Right side)
            FloatingActionButton(
                onClick = {
                    val pos = mapViewState.locationState.getCurrentLocation()?.position
                        ?: currentLocation
                        ?: MapViewModel.DEFAULT_AMSTERDAM_LOCATION
                    viewModel.updateCurrentLocation(pos)
                    mapViewState.cameraState.moveCamera(
                        CameraOptions(
                            position = pos,
                            zoom = 14.0
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 120.dp)
                    .testTag("recenter_location_fab"),
                containerColor = Slate800,
                contentColor = EmeraldMint,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter on My Location"
                )
            }

            // Bottom Route Control Panel Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGlassDark),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .border(1.dp, CardBorderDark, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .testTag("route_control_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title and Route Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = "Route Planning",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Route Planner",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (routeState is RouteUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = EmeraldMint,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // Preset Chips for quick destination testing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.presets.forEach { preset ->
                            val isSelected = destLat == preset.latitude.toString() &&
                                    destLng == preset.longitude.toString()
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset.title, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldMint,
                                    selectedLabelColor = Color.Black,
                                    selectedLeadingIconColor = Color.Black,
                                    containerColor = Slate700,
                                    labelColor = Color.White,
                                    iconColor = CyanAccent
                                )
                            )
                        }
                    }

                    // Destination Coordinates Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = destLat,
                            onValueChange = { viewModel.onDestinationLatChanged(it) },
                            label = { Text("Dest Latitude", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("destination_lat_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = Slate600,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = CyanAccent,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        OutlinedTextField(
                            value = destLng,
                            onValueChange = { viewModel.onDestinationLngChanged(it) },
                            label = { Text("Dest Longitude", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("destination_lng_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = Slate600,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = CyanAccent,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )
                    }

                    // Success Result Metrics Banner
                    AnimatedVisibility(
                        visible = routeState is RouteUiState.Success,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        if (routeState is RouteUiState.Success) {
                            val success = routeState as RouteUiState.Success
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate900),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EmeraldMint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .testTag("route_result_card")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "DISTANCE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = success.distanceFormatted,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldMint
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .width(1.dp)
                                            .background(Slate700)
                                    )

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "EST. TRAVEL TIME",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = success.durationFormatted,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Error Notification Banner
                    AnimatedVisibility(
                        visible = routeState is RouteUiState.Error,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        if (routeState is RouteUiState.Error) {
                            val error = routeState as RouteUiState.Error
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CoralRed, RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Error",
                                        tint = CoralRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error.message,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissError() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Plan Route Action Button
                    Button(
                        onClick = {
                            val userLoc = mapViewState.locationState.getCurrentLocation()?.position
                                ?: currentLocation
                            viewModel.planRoute(userLoc)
                        },
                        enabled = routeState !is RouteUiState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldMint,
                            contentColor = Color.Black,
                            disabledContainerColor = Slate700,
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("plan_route_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (routeState is RouteUiState.Loading) "Calculating Route..." else "Plan Route",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
