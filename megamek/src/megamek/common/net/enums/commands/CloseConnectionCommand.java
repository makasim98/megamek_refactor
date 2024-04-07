package megamek.common.net.enums.commands;

import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.packets.Packet;
import megamek.server.GameManager;

public class CloseConnectionCommand extends AbstractPacketCommand{
    public CloseConnectionCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        // We have a client going down!
        AbstractConnection c = server.getConnection(connId);
        if (c != null) {
            c.close();
        }
    }
}
