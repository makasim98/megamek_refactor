package megamek.common.net.enums.commands;

import megamek.MMConstants;
import megamek.MegaMek;
import megamek.Version;
import megamek.common.Player;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

public class ClientVersionsCommand extends AbstractPacketCommand{
    public ClientVersionsCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        final boolean valid = receivePlayerVersion(packet, connId);
        if (valid) {
            server.sendToPending(connId, new Packet(PacketCommand.SERVER_GREETING));
        } else {
            server.sendToPending(connId, new Packet(PacketCommand.ILLEGAL_CLIENT_VERSION, MMConstants.VERSION));
            server.getPendingConnection(connId).close();
        }
    }

    private boolean receivePlayerVersion(Packet packet, int connId) {
        final Version version = (Version) packet.getObject(0);
        if (!MMConstants.VERSION.is(version)) {
            final String message = String.format("Client/Server Version Mismatch -- Client: %s, Server: %s",
                    version, MMConstants.VERSION);
            LogManager.getLogger().error(message);

            final Player player = server.getPlayer(connId);
            manager.sendServerChat(String.format("For %s, Server reports:%s%s",
                    ((player == null) ? "unknown player" : player.getName()), System.lineSeparator(),
                    message));
            return false;
        }

        final String clientChecksum = (String) packet.getObject(1);
        final String serverChecksum = MegaMek.getMegaMekSHA256();
        final String message;

        // print a message indicating client doesn't have jar file
        if (clientChecksum == null) {
            message = "Client Checksum is null. Client may not have a jar file";
            LogManager.getLogger().info(message);
            // print message indicating server doesn't have jar file
        } else if (serverChecksum == null) {
            message = "Server Checksum is null. Server may not have a jar file";
            LogManager.getLogger().info(message);
            // print message indicating a client/server checksum mismatch
        } else if (!clientChecksum.equals(serverChecksum)) {
            message = String.format("Client/Server checksum mismatch. Server reports: %s, Client reports %s",
                    serverChecksum, clientChecksum);
            LogManager.getLogger().warn(message);
        } else {
            message = "";
        }

        // Now, if we need to, send message!
        if (message.isEmpty()) {
            LogManager.getLogger().info("SUCCESS: Client/Server Version (" + version + ") and Checksum ("
                    + clientChecksum + ") matched");
        } else {
            Player player = server.getPlayer(connId);
            manager.sendServerChat(String.format("For %s, Server reports:%s%s",
                    ((player == null) ? "unknown player" : player.getName()), System.lineSeparator(),
                    message));
        }

        return true;
    }
}
