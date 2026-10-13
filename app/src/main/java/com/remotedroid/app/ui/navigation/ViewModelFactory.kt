package com.remotedroid.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Factory generica usada para injetar dependencias simples (ex: ConnectionHistoryStore)
 * sem precisar de um framework de DI. O projeto e pequeno o suficiente para isso ser
 * mais claro do que configurar Hilt/Koin.
 */
class SimpleViewModelFactory(
    private val creator: () -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        creator() as T
}
