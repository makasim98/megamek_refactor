package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class UnimplementedCommand extends AbstractPacketCommand{
    public UnimplementedCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
