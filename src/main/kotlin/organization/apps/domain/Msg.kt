package organization.apps.domain

data class TransferMsg (
    // The Payload to send
    var payloadB64: String?,
    // 0 - Connect
    // 5 - Connected
    // 10 - Disconnect
    // 20 - transfer
    var transferType: Int,
    var clientId: String,
    var socketId: Long
) {
    companion object {
        val TRANSFER_TYPE_CONNECT = 0
        val TRANSFER_TYPE_CONNECTED = 5
        val TRANSFER_TYPE_DISCONNECT = 10
        val TRANSFER_TYPE_TRANSFER = 20
        val TRANSFER_TYPE_KEEPALIVE = 100
    }
}

data class ConnectRequest (
        val destHost: String,
        val destPort: Int
)