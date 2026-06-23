package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.extractor.SourceExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val repository: HistoryRepository

    val historyList: StateFlow<List<HistoryEntity>>

    // Flow representing current Screen / Overlay state
    private val _viewState = MutableStateFlow(ViewState.SPLASH)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    // Flow for current active extraction progress
    private val _progressStep = MutableStateFlow("Decoding sources...")
    val progressStep: StateFlow<String> = _progressStep.asStateFlow()

    private val _progressPercentage = MutableStateFlow(0)
    val progressPercentage: StateFlow<Int> = _progressPercentage.asStateFlow()

    // Last successful extraction result
    private val _lastResult = MutableStateFlow<SourceExtractor.ExtractionResult?>(null)
    val lastResult: StateFlow<SourceExtractor.ExtractionResult?> = _lastResult.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _loadingInstalledApps = MutableStateFlow(false)
    val loadingInstalledApps: StateFlow<Boolean> = _loadingInstalledApps.asStateFlow()

    private val _pendingExtraction = MutableStateFlow<ExtractionRequest?>(null)
    val pendingExtraction: StateFlow<ExtractionRequest?> = _pendingExtraction.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("nova_extractor_prefs", Context.MODE_PRIVATE)

    private val _isFirstRun = MutableStateFlow(sharedPrefs.getBoolean("first_run", true))
    val isFirstRun: StateFlow<Boolean> = _isFirstRun.asStateFlow()

    enum class ViewState {
        SPLASH,
        MAIN,
        PROCESSING,
        RESULT
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = HistoryRepository(database.historyDao())
        
        historyList = repository.historyList
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Automatically close Splash screen after exactly 3.0 seconds (3000ms)
        Handler(Looper.getMainLooper()).postDelayed({
            _viewState.value = ViewState.MAIN
        }, 3000)
    }

    fun dismissDisclaimer() {
        sharedPrefs.edit().putBoolean("first_run", false).apply()
        _isFirstRun.value = false
    }

    /**
     * Launch offline extraction task on input Uri (from file picker or share sheet).
     */
    fun startExtraction(uri: Uri, customFileName: String? = null) {
        viewModelScope.launch {
            _viewState.value = ViewState.PROCESSING
            _progressPercentage.value = 5
            _progressStep.value = "Preparing workspace..."

            val context = getApplication<Application>()
            
            // Validate the file headers first
            val validation = validateFile(context, uri, customFileName)
            if (!validation.isValid) {
                _viewState.value = ViewState.MAIN
                Toast.makeText(context, "Validation Error: ${validation.errorMessage}", Toast.LENGTH_LONG).show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val result = SourceExtractor.extract(context, uri, customFileName, object : SourceExtractor.ProgressListener {
                        override fun onProgress(percentage: Int, step: String) {
                            _progressPercentage.value = percentage
                            _progressStep.value = step
                        }
                    })

                    // Persist entry to database
                    val entity = HistoryEntity(
                        fileName = getFileNameFromUri(context, uri, customFileName) ?: "unknown_package",
                        fileSize = result.sizeFormatted,
                        fileType = result.fileType,
                        status = if (result.success) "SUCCESS" else "FAILED",
                        extractedClassesCount = result.classesCount,
                        extractedScriptsCount = result.scriptsCount,
                        errorMessage = result.error,
                        outputPath = result.zipFilePath
                    )
                    repository.insertHistory(entity)

                    withContext(Dispatchers.Main) {
                        _lastResult.value = result
                        _viewState.value = ViewState.RESULT
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Extraction aborted due to error", e)
                    val failedEntity = HistoryEntity(
                        fileName = getFileNameFromUri(context, uri, customFileName) ?: "unknown_package",
                        fileSize = "0 B",
                        fileType = "UNKNOWN",
                        status = "FAILED",
                        errorMessage = e.localizedMessage ?: "Critical extract exception"
                    )
                    repository.insertHistory(failedEntity)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Extraction failure: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        _viewState.value = ViewState.MAIN
                    }
                }
            }
        }
    }

    fun cancelProcessing() {
        _viewState.value = ViewState.MAIN
        Toast.makeText(getApplication(), "Extraction cancelled.", Toast.LENGTH_SHORT).show()
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun navigateToMain() {
        _viewState.value = ViewState.MAIN
    }

    data class FileValidation(val isValid: Boolean, val errorMessage: String?)

    private fun validateFile(context: Context, uri: Uri, customFileName: String? = null): FileValidation {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileNameFromUri(context, uri, customFileName)?.lowercase() ?: ""

            // Open input stream to read magic bytes
            val inputStream = contentResolver.openInputStream(uri) ?: return FileValidation(false, "File stream could not be read.")
            val magicBuffer = ByteArray(4)
            val bytesRead = inputStream.read(magicBuffer)
            inputStream.close()

            if (bytesRead < 4) {
                return FileValidation(false, "Header corrupt: empty or truncated file.")
            }

            // ZIP files start with 'PK' and magic byte 0x50 0x4B 0x03 0x04
            val isZip = magicBuffer[0] == 0x50.toByte() && magicBuffer[1] == 0x4B.toByte()
            if (!isZip) {
                return FileValidation(false, "File magic bytes mismatch: Not a valid ZIP file.")
            }

            // High-efficiency fast bypass for standard extensions
            if (fileName.endsWith(".apk")) {
                return FileValidation(true, null)
            }
            if (fileName.endsWith(".ipa")) {
                return FileValidation(true, null)
            }

            // Do deep validation checking file structure for non-standard files
            var hasAndroidManifest = false
            var hasIpaPayload = false

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val zipIn = ZipInputStream(stream)
                var entry = zipIn.nextEntry
                var counter = 0
                // Scan up to 1000 entries (or more) to avoid false-negatives on files with many internal structures
                while (entry != null && counter < 1000) {
                    counter++
                    val name = entry.name.lowercase()
                    if (name.endsWith("androidmanifest.xml")) {
                        hasAndroidManifest = true
                    }
                    if (name.startsWith("payload/") && name.contains(".app/")) {
                        hasIpaPayload = true
                    }
                    zipIn.closeEntry()
                    if (hasAndroidManifest || hasIpaPayload) {
                        break
                    }
                    entry = zipIn.nextEntry
                }
                zipIn.close()
            }

            if (hasAndroidManifest || hasIpaPayload) {
                return FileValidation(true, null)
            }

            return FileValidation(false, "Unknown file format! Must contain APK Manifest or iOS IPAs structure.")

        } catch (e: Exception) {
            Log.e(TAG, "File validation anomaly detected", e)
            return FileValidation(false, "Exception reading container: ${e.localizedMessage}")
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri, customFileName: String? = null): String? {
        if (customFileName != null) return customFileName
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast("/")
        }
        return name
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingInstalledApps.value = true
            val context = getApplication<Application>()
            val pm = context.packageManager
            val apps = mutableListOf<InstalledApp>()
            try {
                // Retrieve all installed applications
                val packages = pm.getInstalledApplications(0)
                for (appInfo in packages) {
                    // Check if they are launchers or non-system apps
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val sourceDir = appInfo.sourceDir ?: continue
                    val file = File(sourceDir)
                    if (!file.exists()) continue
                    
                    val sizeFormatted = formatFileSize(file.length())
                    var version = "1.0"
                    try {
                        val pi = pm.getPackageInfo(packageName, 0)
                        version = pi.versionName ?: "1.0"
                    } catch (e: Exception) {}

                    apps.add(InstalledApp(label, packageName, version, sizeFormatted, sourceDir, isSystem))
                }
                // Sort launcher apps and alphabetical user apps and fallback
                apps.sortWith(compareBy<InstalledApp> { it.isSystem }.thenBy { it.name.lowercase() })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading installed apps", e)
            }
            _installedApps.value = apps
            _loadingInstalledApps.value = false
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Share zipped source code in system Share sheet.
     */
    fun shareOutputZip(context: Context, path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "Zip file not found at output location", Toast.LENGTH_SHORT).show()
                return
            }

            // We must configure and use FileProvider for sharing
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share NOVA extracted Source Zip"))
        } catch (e: Exception) {
            Log.e(TAG, "File sharing exception", e)
            Toast.makeText(context, "Could not share output file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerExtractionRequest(uri: Uri, customFileName: String? = null) {
        val name = getFileNameFromUri(getApplication(), uri, customFileName) ?: "unknown_package"
        _pendingExtraction.value = ExtractionRequest(uri, name, customFileName)
    }

    fun confirmExtraction() {
        val req = _pendingExtraction.value ?: return
        _pendingExtraction.value = null
        startExtraction(req.uri, req.customFileName)
    }

    fun cancelExtraction() {
        _pendingExtraction.value = null
    }
}

data class ExtractionRequest(
    val uri: Uri,
    val resolvedName: String,
    val customFileName: String? = null
)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val version: String,
    val sizeFormatted: String,
    val sourceDir: String,
    val isSystem: Boolean
)
