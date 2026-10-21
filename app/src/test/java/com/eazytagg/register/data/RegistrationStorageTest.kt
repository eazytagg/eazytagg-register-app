package com.eazytagg.register.data

import android.util.AtomicFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Emulate Android's replace-existing rename on the Windows headless test host.
@Implements(AtomicFile::class)
class AndroidAtomicRename {
    @RealObject lateinit var real: AtomicFile
    @Implementation fun finishWrite(stream: FileOutputStream?) {
        if (stream == null) return
        stream.fd.sync(); stream.close()
        Files.move(File(real.baseFile.path + ".new").toPath(), real.baseFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [AndroidAtomicRename::class])
class RegistrationStorageTest {
    private fun record() = QueuedOp(101, "TEST", "{\"name\":\"Synthetic\"}", "no", false, 123456, "Synthetic registration")
    @Test fun noConsentAndOriginalRegistrationTimeSurviveDeviceStorage() {
        val app = RuntimeEnvironment.getApplication()
        Store(app).saveQueue(listOf(record()))
        val restored = Store(app).loadQueue().single()
        assertEquals("no", restored.consent)
        assertEquals(123456L, restored.at)
        assertEquals(record().entryData, restored.entryData)
    }
    @Test fun replacingAQueueDoesNotResurrectAnAcknowledgedRegistration() {
        val app = RuntimeEnvironment.getApplication()
        val store = Store(app)
        store.saveQueue(listOf(record(), record().copy(id = 102)))
        store.saveQueue(listOf(record().copy(id = 102)))
        assertEquals(listOf(102L), Store(app).loadQueue().map { it.id })
        store.saveQueue(emptyList())
        assertTrue(Store(app).loadQueue().isEmpty())
    }
    @Test fun itemsNeedingAttentionKeepTheirErrorAndData() {
        val app = RuntimeEnvironment.getApplication()
        Store(app).saveQueue(listOf(record().copy(failed = true, error = "Review required")))
        val restored = Store(app).loadQueue().single()
        assertTrue(restored.failed)
        assertEquals("Review required", restored.error)
        assertEquals("no", restored.consent)
    }
}
