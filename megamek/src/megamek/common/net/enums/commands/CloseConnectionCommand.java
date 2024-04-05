package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class CloseConnectionCommand extends AbstractPacketCommand{
    public CloseConnectionCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
