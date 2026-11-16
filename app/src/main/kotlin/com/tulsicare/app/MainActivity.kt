package com.tulsicare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.feature.auth.AuthScreen
import com.tulsicare.app.feature.auth.AuthViewModel
import com.tulsicare.app.feature.family.FamilyGroupScreen
import com.tulsicare.app.feature.family.FamilyGroupViewModel
import com.tulsicare.app.feature.history.HistoryScreen
import com.tulsicare.app.feature.history.HistoryViewModel
import com.tulsicare.app.feature.home.HomeScreen
import com.tulsicare.app.feature.home.HomeViewModel
import com.tulsicare.app.feature.medicine.AddEditMedicineScreen
import com.tulsicare.app.feature.medicine.AddEditMedicineViewModel
import com.tulsicare.app.feature.settings.BatteryOptimizationScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TulsiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TulsiNavigation()
                }
            }
        }
    }
}

@Composable
fun TulsiNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val startDestination = if (authState.currentUser != null) "home" else "auth"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("auth") {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToAddMedicine = {
                    navController.navigate("add_medicine")
                },
                onNavigateToEditMedicine = { medicineId ->
                    navController.navigate("edit_medicine/$medicineId")
                },
                onNavigateToBatterySettings = {
                    navController.navigate("battery_settings")
                },
                onNavigateToFamily = {
                    navController.navigate("family")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("history") {
            val viewModel: HistoryViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("family") {
            val viewModel: FamilyGroupViewModel = hiltViewModel()
            FamilyGroupScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("add_medicine") {
            val viewModel: AddEditMedicineViewModel = hiltViewModel()
            AddEditMedicineScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "edit_medicine/{medicineId}",
            arguments = listOf(
                navArgument("medicineId") { type = NavType.StringType }
            )
        ) {
            val viewModel: AddEditMedicineViewModel = hiltViewModel()
            AddEditMedicineScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("battery_settings") {
            BatteryOptimizationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
