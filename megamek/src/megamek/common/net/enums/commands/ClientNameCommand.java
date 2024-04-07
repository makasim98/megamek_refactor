package megamek.common.net.enums.commands;

import megamek.common.Player;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.preference.PreferenceManager;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

import java.net.InetAddress;
import java.util.Enumeration;

public class ClientNameCommand extends AbstractPacketCommand{
    public ClientNameCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        receivePlayerName(packet, connId);
    }

    /**
     * Receives a player name, sent from a pending connection, and connects that
     * connection.
     */
    private void receivePlayerName(Packet packet, int connId) {
        final AbstractConnection conn = server.getPendingConnection(connId);
        String name = (String) packet.getObject(0);
        boolean isBot = (boolean) packet.getObject(1);
        boolean returning = false;

        // this had better be from a pending connection
        if (conn == null) {
            LogManager.getLogger().warn("Got a client name from a non-pending connection");
            return;
        }

        // check if they're connecting with the same name as a ghost player
        for (Enumeration<Player> i = server.getGame().getPlayers(); i.hasMoreElements(); ) {
            Player player = i.nextElement();
            if (player.getName().equals(name)) {
                if (player.isGhost()) {
                    returning = true;
                    player.setGhost(false);
                    player.setBot(isBot);
                    // switch id
                    connId = player.getId();
                    conn.setId(connId);
                }
            }
        }

        if (!returning) {
            // Check to avoid duplicate names...
            server.sendToPending(connId, new Packet(PacketCommand.SERVER_CORRECT_NAME, correctDupeName(name)));
        }

        // right, switch the connection into the "active" bin
        server.removePendingConnection(conn);
        server.addConnection(conn);

        //Send leaderboard rankings to client
        server.send(connId, new Packet(PacketCommand.LEADERBOARD_UPDATE, server.getLeaderboardManager().getRankings()));

        // add and validate the player info
        if (!returning) {
            server.addNewPlayer(connId, name, isBot);
        }

        // if it is not the lounge phase, this player becomes an observer
        Player player = server.getPlayer(connId);
        if (!server.getGame().getPhase().isLounge() && (null != player)
                && (server.getGame().getEntitiesOwnedBy(player) < 1)) {
            player.setObserver(true);
        }

        // send the player the motd
        server.sendServerChat(connId, server.getMotd());

        // send info that the player has connected
        transmitPlayerConnect(player);

        // tell them their local playerId
        server.send(connId, new Packet(PacketCommand.LOCAL_PN, connId));

        // send current game info
        server.sendCurrentInfo(connId);

        final boolean showIPAddressesInChat = PreferenceManager.getClientPreferences().getShowIPAddressesInChat();

        try {
            InetAddress[] addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
            for (InetAddress address : addresses) {
                LogManager.getLogger().info("s: machine IP " + address.getHostAddress());
                if (showIPAddressesInChat) {
                    server.sendServerChat(connId, "Machine IP is " + address.getHostAddress());
                }
            }
        } catch (Exception ignored) {

        }

        LogManager.getLogger().info("s: listening on port " + server.getServerSocket().getLocalPort());
        if (showIPAddressesInChat) {
            // Send the port we're listening on. Only useful for the player
            // on the server machine to check.
            server.sendServerChat(connId, "Listening on port " + server.getServerSocket().getLocalPort());
        }

        // Get the player *again*, because they may have disconnected.
        player = server.getPlayer(connId);
        if (null != player) {
            String who = player.getName() + " connected from " + server.getConnection(connId).getInetAddress();
            LogManager.getLogger().info("s: player #" + connId + ", " + who);
            if (showIPAddressesInChat) {
                server.sendServerChat(who);
            }
        } // Found the player
    }

    /**
     * Correct a duplicate player name
     *
     * @param oldName the <code>String</code> old player name, that is a duplicate
     * @return the <code>String</code> new player name
     */
    private String correctDupeName(String oldName) {
        for (Enumeration<Player> i = server.getGame().getPlayers(); i.hasMoreElements(); ) {
            Player player = i.nextElement();
            if (player.getName().equals(oldName)) {
                // We need to correct it.
                String newName = oldName;
                int dupNum;
                try {
                    dupNum = Integer.parseInt(oldName.substring(oldName.lastIndexOf('.') + 1));
                    dupNum++;
                    newName = oldName.substring(0, oldName.lastIndexOf('.'));
                } catch (Exception e) {
                    // If this fails, we don't care much.
                    // Just assume it's the first time for this name.
                    dupNum = 2;
                }
                newName = newName.concat(".").concat(Integer.toString(dupNum));
                return correctDupeName(newName);
            }
        }
        return oldName;
    }

    /**
     * Sends out player info to all connections
     */
    private void transmitPlayerConnect(Player player) {
        for (var connection : server.getConnections()) {
            var playerId = player.getId();
            connection.send(server.createPlayerConnectPacket(player, playerId != connection.getId()));
        }
    }
}
