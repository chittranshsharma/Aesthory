package app.aesthory.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class AndroidStepsSource(private val context: Context) : StepsSource {
    companion object { val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class)) }
    override suspend fun access(): StepsAccess = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> StepsAccess.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAccess.UPDATE
        else -> if (HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)) StepsAccess.AVAILABLE else StepsAccess.PERMISSION
    }
    override suspend fun total(start: Instant, end: Instant): StepTotal {
        val result = HealthConnectClient.getOrCreate(context).aggregate(AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, end)))
        // Health Connect reconciles overlapping activity sources. Never sum raw records or per-app totals.
        return StepTotal(result[StepsRecord.COUNT_TOTAL], result.dataOrigins.map { origin ->
            try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(origin.packageName, 0)).toString() }
            catch (_: Exception) { origin.packageName }
        }.toSet())
    }
}
