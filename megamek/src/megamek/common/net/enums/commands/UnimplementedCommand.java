package megamek.common.net.enums.commands;

import megamek.common.net.packets.Packet;
import megamek.server.GameManager;

public class UnimplementedCommand extends AbstractPacketCommand{
    public UnimplementedCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {

    }
}
