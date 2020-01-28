package organization.apps.service

import com.google.common.io.BaseEncoding
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import organization.apps.configuration.ApplicationConfig
import org.springframework.stereotype.Component
import organization.apps.domain.ConnectRequest
import organization.apps.domain.TransferMsg
import organization.apps.handler.DiamondHandler
import java.io.OutputStream
import java.net.Socket
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.PostConstruct
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.springframework.beans.factory.annotation.Autowired
import organization.apps.domain.TransferMsgHolder
import java.lang.Exception
import java.net.URI
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Component
class ApplicationService (val applicationConfig: ApplicationConfig) {

    val clientId = UUID.randomUUID().toString()
    val gson = Gson()
    val client = WebSocketClient()


    @PostConstruct
    fun init () {
        // Just need to start once
        client.start()

        connect()
    }

    @Autowired
    lateinit var diamondHandler: DiamondHandler

    fun connect() {
        logger.info("Trying to connect to {}", applicationConfig.diamondProxyUrl)
        val endpointUri = URI(applicationConfig.diamondProxyUrl)

        val request = ClientUpgradeRequest()
        client.connect(diamondHandler, endpointUri, request)
    }

    val socketMap = mutableMapOf<Long, SocketHolder>()
    val connectionSeq = AtomicLong(System.currentTimeMillis())
    val base64Encoding = BaseEncoding.base64()

    // Each Internal Map / socket has a corresponding host
    fun attachSocket(socket: Socket, destIp: String, destPort: Int) {

        val socketId = connectionSeq.incrementAndGet()
        socketMap[socketId] = SocketHolder (
                socket = socket,
                outputStream = socket.getOutputStream()
        )

        sendTransferMsg(
                transferType =  TransferMsg.TRANSFER_TYPE_CONNECT,
                socketId = socketId,
                payload = gson.toJson(
                        ConnectRequest(
                                destHost = destIp,
                                destPort = destPort
                        )
                )
        )

        // Wait for connected then start the thread that will send the srcSocket data over to the diamond-proxy
    }

    // Receive a message from the Diamond-server
    fun handleFrame(payload: String?) {
        if (payload != null) {
            val transferMsgHolder = gson.fromJson(payload, TransferMsgHolder::class.java)
            if (transferMsgHolder.transferMsgHolder.size > 1) {
                logger.debug("Overflow entry {}", transferMsgHolder.transferMsgHolder.size)
            }
            for (msgBody in transferMsgHolder.transferMsgHolder) {
                val transferMsgResponse = gson.fromJson(msgBody, TransferMsg::class.java)

                when (transferMsgResponse.transferType) {
                    TransferMsg.TRANSFER_TYPE_CONNECT -> processConnect(transferMsgResponse)
                    TransferMsg.TRANSFER_TYPE_CONNECTED -> processConnected(transferMsgResponse)
                    TransferMsg.TRANSFER_TYPE_TRANSFER -> processTransfer(transferMsgResponse)
                    TransferMsg.TRANSFER_TYPE_DISCONNECT -> processDisconnect(transferMsgResponse)
                    TransferMsg.TRANSFER_TYPE_KEEPALIVE -> {
                        // Do nothing
//                    logger.debug("Received KeepAlive from peer nodes")
                    }
                }
            }
        }
    }

    fun processConnect (transferMsgResponse: TransferMsg) {

        // Create a socket Holder
        val connectRequest = gson.fromJson(transferMsgResponse.payloadB64, ConnectRequest::class.java)
        logger.info("Received Diamond Request socket: {} for Target {} : {} ", transferMsgResponse.socketId, connectRequest.destHost, connectRequest.destPort)

        val socket = Socket (connectRequest.destHost, connectRequest.destPort)
        socketMap[transferMsgResponse.socketId] = SocketHolder(
                socket = socket,
                outputStream = socket.getOutputStream()
        )
        sendTransferMsg(
                transferType =  TransferMsg.TRANSFER_TYPE_CONNECTED,
                socketId = transferMsgResponse.socketId
        )

        // Start the Thread that will read from the socket
        thread (start = true) {
            try {
                socket.use {
                    socket.getInputStream().use {inputStream ->
                        val byteArray = ByteArray(1024*100)
                        var bytesRead: Int

                        // Loop to send data
                        do {
                            bytesRead = inputStream.read(byteArray)
                            logger.debug("Received {} with size {}", transferMsgResponse.socketId, bytesRead)
                            if (bytesRead != -1) {
                                sendTransferMsg(
                                        transferType =  TransferMsg.TRANSFER_TYPE_TRANSFER,
                                        socketId = transferMsgResponse.socketId,
                                        payload = base64Encoding.encode(byteArray, 0, bytesRead)
                                )
                            }
                        } while (bytesRead != -1)
                    }
                }
            }catch (e: Exception) {
                logger.error("UnExpected error for socket " + transferMsgResponse.socketId, e)
            }

            sendTransferMsg(
                    transferType =  TransferMsg.TRANSFER_TYPE_DISCONNECT,
                    socketId = transferMsgResponse.socketId,
                    payload = null
            )
            logger.debug("Socket {} Closed ", transferMsgResponse.socketId)
        }
    }

    // Connection connected on the corresponding side
    // Start the thread to receive the data stream from the socket
    fun processConnected (transferMsgResponse: TransferMsg) {
        val socketHolder = socketMap[transferMsgResponse.socketId]
        val socketId = transferMsgResponse.socketId

        logger.info("Received Diamond Connected for socket ", transferMsgResponse.socketId)

        // Start the thread to receive the data
        thread(start = true) {
            try {
                val byteArray = ByteArray(10240)
                val socket = socketHolder!!.socket
                socket.use {inputSocket ->
                    inputSocket.getInputStream().use { istream->

                        // Loop to send data
                        logger.debug("Socket {} Stream started", transferMsgResponse.socketId)
                        do {
                            val bytesRead = istream.read(byteArray)

                            // Push to the attached session
                            if (bytesRead != -1) {
                                sendTransferMsg(
                                        transferType =  TransferMsg.TRANSFER_TYPE_TRANSFER,
                                        socketId = socketId,
                                        payload = base64Encoding.encode(byteArray, 0, bytesRead)
                                )
                            }

                        } while (bytesRead != -1)
                    }
                }
            } finally {
                sendTransferMsg(
                        transferType =  TransferMsg.TRANSFER_TYPE_DISCONNECT,
                        socketId = socketId,
                        payload = null
                )
                logger.debug("Socket {} Closed ", transferMsgResponse.socketId)

            }
        }
    }

    // Corresponding side indicate socket killed
    fun processDisconnect (transferMsgResponse: TransferMsg) {
        val socketHolder = socketMap[transferMsgResponse.socketId]
        logger.info("Received Diamond DisConnect socket: {} ", transferMsgResponse.socketId)

        // Close the socket and remove the holder
        socketHolder!!.outputStream.close()
        socketHolder.socket.close()

        socketMap.remove(transferMsgResponse.socketId)
    }

    // Corresponding side received data on the socket
    fun processTransfer (transferMsgResponse: TransferMsg) {
        //logger.info("Received Diamond Transfer {} ", transferMsgResponse.socketId)
        val socketHolder = socketMap[transferMsgResponse.socketId]

        socketHolder!!.outputStream.write(
                base64Encoding.decode(transferMsgResponse.payloadB64)
        )
        socketHolder.outputStream.flush()
    }

    val sendQueue = LinkedTransferQueue<String> ()

    fun sendTransferMsg (transferType: Int, socketId: Long = -1, payload: String? = null) {
        val msgPayload = gson.toJson(
                TransferMsg(
                        payloadB64 = payload,
                        clientId = clientId,
                        socketId =  socketId,
                        transferType = transferType
                )
        )
        sendQueue.put(msgPayload)
    }

    // Called by diamondHandler once onOpen is called
    lateinit var session: Session
    fun attachSession (session: Session) {
        this.session = session

        // Start the thread that sends the Keepalive
        thread (start = true) {
            while (true) {
                // Every 5 seconds
                Thread.sleep(5000)

                logger.debug("Sending keepalive")
                sendTransferMsg(
                        transferType =  TransferMsg.TRANSFER_TYPE_KEEPALIVE
                )
            }
        }

        // Received from the SendQueue and sent to the WebSocket server
        thread (start = true ) {
            var emptyMsgCount = 0
            var bodySize = 0;
            var transferMsgHolder = TransferMsgHolder ()

            while (true) {
                val sendBody = sendQueue.poll(50, TimeUnit.MILLISECONDS)
                var flushBuffer = false

                if (sendBody != null) {
                    emptyMsgCount = 0

                    if ((bodySize + sendBody.length) > 60000) {
                        logger.debug("Send Size {} and eMsgCount {}", bodySize, emptyMsgCount)
                        session.remote.sendString(gson.toJson(transferMsgHolder))

                        bodySize = 0
                        emptyMsgCount = 0
                        transferMsgHolder = TransferMsgHolder ()
                    }
                    bodySize = bodySize + sendBody.length
                    transferMsgHolder.transferMsgHolder.add(sendBody)
                } else {
                    emptyMsgCount++;
                }

                // overflow
                if (
                        (bodySize > 0) && (emptyMsgCount > 10)       // Pending Msg waited too long
                ) {
                    logger.debug("Send Size {} and eMsgCount {}", bodySize, emptyMsgCount)
                    session.remote.sendString(gson.toJson(transferMsgHolder))

                    // Reset all the variables
                    bodySize = 0
                    emptyMsgCount = 0
                    transferMsgHolder = TransferMsgHolder ()
                }
            }
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(ApplicationService::class.java)
    }
}

data class SocketHolder (
        val socket: Socket,
        val outputStream: OutputStream
)