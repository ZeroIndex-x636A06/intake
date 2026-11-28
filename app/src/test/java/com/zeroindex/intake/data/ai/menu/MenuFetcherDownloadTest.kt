package com.zeroindex.intake.data.ai.menu

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class MenuFetcherDownloadTest {
    private val url = "https://www.mcdonalds.com/content/dam/sites/ie/nfl/pdf/nutrition/menu.pdf"
    private val denied = javaClass.classLoader!!.getResourceAsStream("menu/akamai-access-denied.html")!!.readBytes()

    private fun fetcher(code: Int, type: String, body: ByteArray, seenHeaders: MutableList<String?> = mutableListOf()): MenuFetcher {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            seenHeaders += chain.request().header("User-Agent")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody(type.toMediaType())).build()
        }).build()
        return MenuFetcher(client)
    }

    @Test
    fun `a 403 access denied page is reported as a refusal and the client stays honest`() = runBlocking<Unit> {
        val agents = mutableListOf<String?>()
        val got = fetcher(403, "text/html", denied, agents).fetch(url)
        assertThat(got).isInstanceOf(Fetched.Refused::class.java)
        val message = (got as Fetched.Refused).message
        assertThat(message).contains("mcdonalds.com refused the download (HTTP 403)")
        assertThat(agents.single()).startsWith("Intake/")
    }

    @Test
    fun `a 200 web page at a pdf address is refused rather than handed to the pdf reader`() = runBlocking<Unit> {
        assertThat(fetcher(200, "text/html", denied).fetch(url)).isInstanceOf(Fetched.Refused::class.java)
    }

    @Test
    fun `a real pdf comes back as a pdf, even with the wrong content type`() = runBlocking<Unit> {
        val pdf = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n".toByteArray()
        assertThat(fetcher(200, "application/pdf", pdf).fetch(url)).isInstanceOf(Fetched.Pdf::class.java)
        assertThat(fetcher(200, "application/octet-stream", pdf).fetch(url)).isInstanceOf(Fetched.Pdf::class.java)
    }
}
