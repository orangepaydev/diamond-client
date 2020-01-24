package organization.apps.handler

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import organization.apps.service.ApplicationService

@WebSocket(maxTextMessageSize = 64 * 1024)
@Component
class DiamondHandler {

    @Autowired
    lateinit var applicationService: ApplicationService

    @OnWebSocketClose
    fun onClose(statusCode: Int, reason: String?) {
        logger.error("Re-Connect {}, {}", statusCode, reason)

        // Force a connect again
        logger.info("Trying to reconnect")
        applicationService.connect()
    }

    @OnWebSocketConnect
    fun onConnect(session: Session) {
        logger.error("Connected to the Session ")
        applicationService.attachSession(session)
    }

    @OnWebSocketMessage
    fun onMessage(msg: String) {
        logger.debug("Received Payload {}", msg)
        applicationService.handleFrame(msg)
    }

    @OnWebSocketError
    fun onError(cause: Throwable) {
        logger.error("Error ", cause)
    }

    companion object {
        val logger = LoggerFactory.getLogger(DiamondHandler::class.java)
    }
}