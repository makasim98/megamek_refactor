package megamek.common.net.enums.commands;

import megamek.common.net.packets.Packet;
import megamek.server.GameManager;
import megamek.server.Server;

public abstract class AbstractPacketCommand {

    protected GameManager manager;
    protected Server server;

    public AbstractPacketCommand(GameManager manager) {
        this.manager = manager;
        this.server = Server.getServerInstance();
    }

    public abstract void handle(int connId, Packet packet);
}
