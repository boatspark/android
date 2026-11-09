package com.boatspark.app.core.network

import com.boatspark.app.core.model.BoatAlerts
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.BoatList
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.UserProfile
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BoatSparkApi {
    @GET("api/v1/user/me")
    suspend fun currentUser(): UserProfile

    @GET("api/v1/boats")
    suspend fun boats(): BoatList

    @GET("api/v1/boats/{boatId}/status")
    suspend fun status(@Path("boatId") boatId: Long): BoatStatus

    @GET("api/v1/boats/{boatId}/alerts")
    suspend fun alerts(
        @Path("boatId") boatId: Long,
        @Query("days") days: Int = 7,
    ): BoatAlerts

    @GET("api/v1/boats/{boatId}/history")
    suspend fun history(@Path("boatId") boatId: Long): BoatHistory
}
