package com.spot2ytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleApiErrorTest {
    private fun body(code: Int, reason: String, message: String) =
        """{"error":{"code":$code,"message":"$message","errors":[{"message":"$message","domain":"youtube.quota","reason":"$reason"}]}}"""

    @Test fun readsQuotaReason() {
        val e = GoogleApiError.from(HttpException(403, body(403, "quotaExceeded", "The request cannot be completed because you have exceeded your <a href='/youtube/v3/getting-started#quota'>quota</a>.")))
        assertEquals("quotaExceeded", e.reason)
        assertEquals("The request cannot be completed because you have exceeded your quota.", e.googleMessage)
        assertTrue(e.stopsWholeExport)
        assertTrue(e.explanation.startsWith("Google's daily limit is used up"))
    }

    @Test fun readsReasonFromDetailsWhenErrorsMissing() {
        val e = GoogleApiError.from(HttpException(403,
            """{"error":{"code":403,"message":"Request had insufficient authentication scopes.","status":"PERMISSION_DENIED","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}"""))
        assertEquals("ACCESS_TOKEN_SCOPE_INSUFFICIENT", e.reason)
        assertTrue(e.explanation.contains("doesn't have permission"))
    }

    @Test fun expiredTokenStopsExport() {
        val e = GoogleApiError.from(HttpException(401, body(401, "authError", "Invalid Credentials")))
        assertTrue(e.stopsWholeExport)
        assertTrue(e.explanation.contains("expired"))
    }

    @Test fun singleBadVideoIsSkippedNotFatal() {
        val e = GoogleApiError.from(HttpException(404, body(404, "videoNotFound", "Video not found.")))
        assertFalse(e.stopsWholeExport)
    }

    @Test fun survivesNonJsonBody() {
        val e = GoogleApiError.from(HttpException(403, "<html>Forbidden</html>"))
        assertEquals(null, e.reason)
        assertEquals("Google refused the request (HTTP 403).", e.explanation)
    }
}
