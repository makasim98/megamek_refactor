package megamek.common.net.enums.commands;

import megamek.server.GameManager;

public class ChatCommand extends AbstractPacketCommand{
    public ChatCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle() {

    }
}
