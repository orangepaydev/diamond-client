package organization.apps.configuration

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import javax.annotation.PostConstruct


@Configuration
@PropertySource(value=["classpath:config/application.yml", "conf/application-\${env:prod}.yml"], ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "application")
class ApplicationConfig {
    lateinit var applicationName:String

    @PostConstruct
    fun inif() {
        logger.info("ApplicationConfig {}", toString())
    }

    lateinit var diamondProxyUrl: String
    var proxyPort: Int = 0
    lateinit var diamonPacFile: String

    @Bean
    fun gson(): Gson {
        return Gson()
    }

    override fun toString(): String {
        return "ApplicationConfig(applicationName='$applicationName')"
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ApplicationConfig::class.java);
    }
}