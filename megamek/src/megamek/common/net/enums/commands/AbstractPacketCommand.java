package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public abstract class AbstractPacketCommand {

    protected GameManager manager;

    public AbstractPacketCommand(GameManager manager) {
        this.manager = manager;
    }

    public abstract void handle();
}
