package com.emulnk

import android.app.Application
import com.emulnk.core.MemoryService
import com.emulnk.data.MemoryRepository

class EmuLnkApplication : Application() {
    val memoryService: MemoryService by lazy {
        MemoryService(MemoryRepository())
    }
}
