package app.aesthory

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aesthory.health.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepsDeviceTest {
    @Test fun frameworkStepsReadUsesGrantedReadOnlyAccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.health.READ_STEPS" in permissions)
        assertFalse("android.permission.health.WRITE_STEPS" in permissions)
        val source = AndroidStepsSource(context)
        // Test setup grants READ_STEPS through the owned emulator's real permission screen.
        assertEquals(StepsAccess.AVAILABLE, source.access())
        val repository = StepsRepository(source)
        repository.refresh()
        assertFalse(repository.state.value.error)
        assertEquals(7, repository.state.value.days.size)
        assertNotNull(repository.state.value.checked)
        repository.refresh()
        assertEquals(7, repository.state.value.days.size)
    }
}
