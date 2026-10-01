package com.locationjoystick.core.location

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.SimpleValidationReportFormat
import org.junit.Assert.fail
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a real HTTP request to the test server and checks the exchange against docs/openapi.yaml.
 * Every HTTP call validates that the request and response match the documented OpenAPI spec.
 */
object ApiContract {
    private val validator: OpenApiInteractionValidator =
        OpenApiInteractionValidator
            .createForSpecificationUrl(System.getProperty("openapi.spec"))
            .withBasePathOverride("/api/v1")
            .build()

    /** Operations exercised during the run, for optional coverage checks. */
    val covered = mutableSetOf<String>()

    data class Result(val code: Int, val body: String)

    /**
     * Calls an endpoint, validates the HTTP exchange against the OpenAPI spec, and returns the response.
     *
     * @param port The port the test server is listening on.
     * @param method HTTP method (GET, POST, PUT, DELETE).
     * @param path The path without /api/v1 prefix, e.g., "favorites", "routes/{id}", "teleport".
     * @param body Request body as JSON string, or null for GET.
     * @param auth Whether to include the Bearer token.
     * @param validateRequest If false, only the response is validated. Use this for tests that
     *        deliberately send malformed input (e.g., bad JSON, out-of-range values) to test error handling.
     * @return Response code and body.
     * @throws AssertionError if the request or response does not match the spec.
     */
    fun call(
        port: Int,
        method: String,
        path: String,
        body: String? = null,
        auth: Boolean = true,
        validateRequest: Boolean = true,
    ): Result {
        val conn = URL("http://localhost:$port/api/v1/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        if (auth) conn.setRequestProperty("Authorization", "Bearer k")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toByteArray()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
        val contentType = conn.contentType ?: "application/json"
        conn.disconnect()

        val requestBuilder = SimpleRequest.Builder(method, "/api/v1/$path")
        if (auth) requestBuilder.withAuthorization("Bearer k")
        if (body != null) requestBuilder.withContentType("application/json").withBody(body)
        val request = requestBuilder.build()

        val response =
            SimpleResponse.Builder(code)
                .withContentType(contentType)
                .apply { if (text.isNotEmpty()) withBody(text) }
                .build()

        val report =
            if (validateRequest) {
                validator.validate(request, response)
            } else {
                validator.validateResponse("/api/v1/$path", Request.Method.valueOf(method), response)
            }
        if (report.hasErrors()) {
            fail(
                "$method /$path -> $code does not match openapi.yaml:\n" +
                    SimpleValidationReportFormat.getInstance().apply(report),
            )
        }
        covered += "$method /$path"
        return Result(code, text)
    }
}
