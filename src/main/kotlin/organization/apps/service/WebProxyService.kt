package organization.apps.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import organization.apps.configuration.ApplicationConfig
import java.lang.Exception
import java.net.ServerSocket
import java.net.Socket
import javax.annotation.PostConstruct
import kotlin.concurrent.thread

@Component
class WebProxyService (val ac: ApplicationConfig) {

    lateinit var serverSocket: ServerSocket
    @Autowired
    lateinit var applicationService: ApplicationService

    @PostConstruct
    fun postInit () {
        // Only start up if Socket is configured
        if (ac.proxyPort == 0) {
            logger.info("Web Proxy Listen Socket disabled")
            return
        }

        logger.info("Web Proxy Listen Socket {}", ac.proxyPort)
        serverSocket = ServerSocket(ac.proxyPort)

        // Start Thread that receives new Socket Connection
        thread (start = true) {
            logger.info("Waiting for new Connection")
            while (true) {
                val newSocket = serverSocket.accept()
                logger.info("Received new connection")

                // Keep scannling till we get the \n
                val inputS = newSocket.getInputStream()
                var c: Int = 100

                var httpInstructionLine: MutableList<Byte> = mutableListOf()

                var host: String = ""
                var port = 0

                while (c != -1) {

                    c = inputS.read()
                    httpInstructionLine.add(c.toByte())

                    if (c == 10) {
                        var lineStr = httpInstructionLine.toByteArray().toString(Charsets.UTF_8)
                        logger.debug("Header {}", lineStr.trim())

                        if (lineStr.startsWith("CONNECT")) {
                            var lineSV = lineStr.split(" ")
                            val connectSV = lineSV[1].split(":")


                            host = connectSV[0]
                            port = Integer.parseInt(connectSV[1])
                        }


                        if (lineStr.trim().equals("")) {
                            break;
                        }
                        httpInstructionLine = mutableListOf()
                    }
                }

                val outputStream = newSocket.getOutputStream()
                outputStream.write("HTTP/1.1 200 OK\n\n".toByteArray())
                outputStream.flush()

                logger.debug("Proxy to {} {}", host, port)
                applicationService.attachSocket(newSocket, host, port)

//                val outgoingSocket = Socket(host, port)
//                Thread(PassThrough(incomingSocket = newSocket, outgoingSocket = outgoingSocket)).start()
//                Thread(PassThrough(incomingSocket = outgoingSocket, outgoingSocket = newSocket)).start()
            }
        }
    }


    companion object {
        val logger = LoggerFactory.getLogger(WebProxyService::class.java)
    }
}

class PassThrough(
        val incomingSocket: Socket,
        val outgoingSocket: Socket
) : Runnable {
    override fun run() {
        logger.debug("Proxy Pass Through Started")
        incomingSocket.use {iSocket ->
            iSocket.getInputStream().use { inputStream ->
                outgoingSocket.use { oSocket->
                    outgoingSocket.getOutputStream().use {outputStream->
                        val byteArray = ByteArray(10240*10)
                        var bytesRead: Int;

                        try {
                            while (true) {
                                bytesRead = inputStream.read(byteArray)
                                if (bytesRead == -1) {
                                    break;
                                }

                                logger.debug("Data {}", bytesRead)
                                outputStream.write(byteArray, 0, bytesRead)
                                outputStream.flush()
                            }
                        } catch (e: Exception) {
                            logger.warn("Exception seen ", e.message)
                        }

                        logger.debug("Stream terminated.  Closing")
                    }
                }
            }
        }

    }

    companion object {
        val logger = LoggerFactory.getLogger(WebProxyService::class.java)
    }
}