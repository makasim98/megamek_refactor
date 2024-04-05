package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class ClientNameCommand extends AbstractPacketCommand{
    public ClientNameCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
