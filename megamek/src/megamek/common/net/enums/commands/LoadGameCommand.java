package megamek.common.net.enums.commands;

import megamek.common.Game;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.packets.Packet;
import megamek.server.GameManager;
import org.apache.logging.log4j.LogManager;

public class LoadGameCommand extends AbstractPacketCommand{
    public LoadGameCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        try {
            server.sendServerChat(server.getPlayer(connId).getName() + " loaded a new game.");
            server.setGame((Game) packet.getObject(0));
            for (AbstractConnection conn : server.getConnections()) {
                server.sendCurrentInfo(conn.getId());
            }
        } catch (Exception e) {
            LogManager.getLogger().error("Error loading save game sent from client", e);
        }
    }
}
