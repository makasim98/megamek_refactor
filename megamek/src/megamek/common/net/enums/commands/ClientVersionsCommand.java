package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class ClientVersionsCommand extends AbstractPacketCommand{
    public ClientVersionsCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
