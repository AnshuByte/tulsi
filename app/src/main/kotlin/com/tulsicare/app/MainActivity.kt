package com.tulsicare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiBottomNavBar
import com.tulsicare.app.core.designsystem.component.TulsiNavTab
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
import com.tulsicare.app.feature.settings.SettingsScreen
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
    val homeViewModel: HomeViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val startDestination = if (authState.currentUser != null) "home" else "auth"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination
    val isBottomBarVisible = currentRoute in listOf("home", "family", "history", "settings")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isBottomBarVisible) {
                TulsiBottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("auth") {
                AuthScreen(
                    viewModel = authViewModel,
                    onAuthSuccess = {
                        homeViewModel.loadData()
                        navController.navigate("home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToAddMedicine = {
                        navController.navigate("add_medicine")
                    },
                    onNavigateToEditMedicine = { medicineId ->
                        navController.navigate("edit_medicine/$medicineId")
                    }
                )
            }

            composable("family") {
                val viewModel: FamilyGroupViewModel = hiltViewModel()
                FamilyGroupScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.navigate("home")
                    }
                )
            }

            composable("history") {
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.navigate("home")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentUser = authState.currentUser ?: homeState.currentUser,
                    isSimpleMode = homeState.isSimpleMode,
                    onToggleSimpleMode = homeViewModel::toggleSimpleMode,
                    onNavigateToReliability = {
                        navController.navigate("reliability")
                    },
                    onTriggerTestAlarm = {
                        homeViewModel.triggerTestAlarm(context)
                    },
                    onSignOut = {
                        authViewModel.signOut()
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
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

            composable("reliability") {
                BatteryOptimizationScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
