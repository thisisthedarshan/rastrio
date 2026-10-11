/**
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.circuitnext.rastrio

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.circuitnext.rastrio.printing.AndroidPrintingPlatform
import com.circuitnext.rastrio.printing.bluetoothPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.circuitnext.rastrio.printing.PrinterSessionStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private lateinit var printingOwner: PrintingOwner
    private lateinit var printing: AndroidPrintingPlatform
    private val bluetoothPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        printing.permissionResult(it)
    }
    private val profilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        printing.profileResult(it)
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        printing.imageResult(if (result.resultCode == RESULT_OK) result.data?.data else null)
    }

    override fun onStart() { super.onStart(); if (::printing.isInitialized) printing.setForeground(true) }
    override fun onStop() { if (::printing.isInitialized) printing.setForeground(false); super.onStop() }
    override fun onDestroy() {
        if (::printing.isInitialized) { printing.bindActions({}, {}); printing.bindImageAction({}) }
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        printingOwner = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PrintingOwner(applicationContext) as T
            }
        })[PrintingOwner::class.java]
        printing = printingOwner.platform
        printing.bindActions(permission = {
            val permission = bluetoothPermission(android.os.Build.VERSION.SDK_INT)
            if (permission == null || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
                printing.permissionResult(true)
            else bluetoothPermissionRequest.launch(permission)
        }, profile = { profilePicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
        printing.bindImageAction {
            imagePicker.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
                putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true)
            })
        }
        setContent {
            RastrIOApp(printingPlatform = printing, printingSessions = printingOwner.sessions)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    RastrIOApp()
}

/** Retains ambiguous delivery evidence across activity recreation, without background printing. */
private class PrintingOwner(context: android.content.Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val platform = AndroidPrintingPlatform(context, scope, {}, {})
    val sessions = PrinterSessionStore(platform::createTransport)
    override fun onCleared() { sessions.cancelAll(); platform.setForeground(false); scope.cancel() }
}
