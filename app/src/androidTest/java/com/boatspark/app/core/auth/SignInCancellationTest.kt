package com.boatspark.app.core.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.boatspark.app.core.config.AppConfig
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInCancellationTest {
    @Test
    fun cancelledBrowserFlowIsNotAnError() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authorizationService = AuthorizationService(context)
        try {
            val repository = AuthRepository(
                AppConfig(),
                authorizationService,
                EncryptedAuthStateStore(context),
            )
            val stateBefore = repository.state.value

            val result = repository.handleAuthorizationResult(
                AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.toIntent(),
            )

            assertTrue(result.exceptionOrNull() is SignInCancelledException)
            assertEquals(stateBefore, repository.state.value)
        } finally {
            authorizationService.dispose()
        }
    }
}
