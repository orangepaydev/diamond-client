package organization.apps.web

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.*
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import java.net.URI


@WebSocket(maxTextMessageSize = 64 * 1024)
class SimpleEchoSocket {

    @OnWebSocketClose
    fun onClose(statusCode: Int, reason: String?) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason)
    }
    lateinit var session: Session
    @OnWebSocketConnect
    fun onConnect(session: Session) {
        System.out.printf("Got connect: %s%n", session)
        this.session = session
    }

    @OnWebSocketMessage
    fun onMessage(msg: String) {
        System.out.printf("Got msg: %s%n", msg)
        session.remote.sendString("Echo " + msg)
    }

    @OnWebSocketError
    fun onError(cause: Throwable) {
        print("WebSocket Error: ")
    }
}

fun main() {
    val echoUri = URI("wss://radiant-eyrie-13424.herokuapp.com/diamondMsg")
    val client = WebSocketClient()
    client.start()

    val request = ClientUpgradeRequest()
    client.connect(SimpleEchoSocket(), echoUri, request)

    while (true) {
        Thread.sleep(1000)
    }
}