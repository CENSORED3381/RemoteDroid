package com.remotedroid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remotedroid.app.core.adb.AdbConnectionManager
import com.remotedroid.app.core.storage.ConnectionHistoryStore
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.remotedroid.app.ui.screens.apps.AppsScreen
import com.remotedroid.app.ui.screens.apps.AppsViewModel
import com.remotedroid.app.ui.screens.connect.ConnectScreen
import com.remotedroid.app.ui.screens.connect.ConnectViewModel
import com.remotedroid.app.ui.screens.deviceinfo.DeviceInfoScreen
import com.remotedroid.app.ui.screens.deviceinfo.DeviceInfoViewModel
import com.remotedroid.app.ui.screens.files.FilesScreen
import com.remotedroid.app.ui.screens.files.FilesViewModel
import com.remotedroid.app.ui.screens.home.HomeScreen
import com.remotedroid.app.ui.screens.logs.LogsScreen
import com.remotedroid.app.ui.screens.logs.LogsViewModel

@Composable
fun RemoteDroidNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyStore = remember(context) { ConnectionHistoryStore(context) }

    NavHost(navController = navController, startDestination = Screen.Connect.route) {
        composable(Screen.Connect.route) {
            val viewModel: ConnectViewModel = viewModel(
                factory = SimpleViewModelFactory { ConnectViewModel(historyStore) }
            )
            ConnectScreen(
                viewModel = viewModel,
                onConnected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Connect.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onDisconnect = {
                    scope.launch {
                        AdbConnectionManager.disconnect()
                        navController.navigate(Screen.Connect.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.DeviceInfo.route) {
            val viewModel: DeviceInfoViewModel = viewModel()
            DeviceInfoScreen(viewModel)
        }

        composable(Screen.Apps.route) {
            val viewModel: AppsViewModel = viewModel()
            AppsScreen(viewModel)
        }

        composable(Screen.Files.route) {
            val viewModel: FilesViewModel = viewModel()
            FilesScreen(viewModel)
        }

        composable(Screen.Logs.route) {
            val viewModel: LogsViewModel = viewModel()
            LogsScreen(viewModel)
        }
    }
}
