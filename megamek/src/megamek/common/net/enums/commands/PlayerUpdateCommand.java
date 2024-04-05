package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class PlayerUpdateCommand extends AbstractPacketCommand{
    public PlayerUpdateCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
