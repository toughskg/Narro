package com.narro.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.narro.app.AppGraph

class AppViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        return AppViewModel(graph) as T
    }
}
