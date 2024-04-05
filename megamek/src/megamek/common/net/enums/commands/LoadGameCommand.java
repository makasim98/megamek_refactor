package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class LoadGameCommand extends AbstractPacketCommand{
    public LoadGameCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
