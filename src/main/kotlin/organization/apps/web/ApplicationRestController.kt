package organization.apps.web

import organization.apps.configuration.ApplicationConfig
import organization.apps.service.ApplicationService
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import organization.apps.handler.DiamondHandler
import java.io.InputStream
import java.nio.charset.Charset
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}

@RestController
@RequestMapping("/proxy")
class ApplicationRestController (val applicationConfig: ApplicationConfig) {

    private val logger = LoggerFactory.getLogger(ApplicationRestController::class.java)

    @Autowired
    private lateinit var gson: Gson

    @Autowired
    private lateinit var applicationService: ApplicationService

    @GetMapping(path=["/pac"])
    fun transferPac(): String {
        logger.debug("Reading Pac File {}", applicationConfig.diamonPacFile)
        return Thread
                .currentThread()
                .contextClassLoader.getResourceAsStream(applicationConfig.diamonPacFile)
                .readTextAndClose()
    }

    @PostMapping(path=["/method1"], consumes = ["application/json"], produces = ["application/json"])
    fun method1(@RequestBody requestBody:String, request: HttpServletRequest, response: HttpServletResponse): String {
        return "{}"
    }

    companion object {
        val logger = LoggerFactory.getLogger(ApplicationRestController::class.java)
    }
}