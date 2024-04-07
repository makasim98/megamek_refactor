package megamek.common.net.enums.commands;

import megamek.common.Player;
import megamek.common.net.packets.Packet;
import megamek.server.GameManager;

public class ChatCommand extends AbstractPacketCommand{

    // Easter eggs. Happy April Fool's Day!!
    private static final String DUNE_CALL = "They tried and failed?";

    private static final String DUNE_RESPONSE = "They tried and died!";

    private static final String STAR_WARS_CALL = "I'd just as soon kiss a Wookiee.";

    private static final String STAR_WARS_RESPONSE = "I can arrange that!";

    private static final String INVADER_ZIM_CALL = "What does the G stand for?";

    private static final String INVADER_ZIM_RESPONSE = "I don't know.";

    private static final String WARGAMES_CALL = "Shall we play a game?";

    private static final String WARGAMES_RESPONSE = "Let's play global thermonuclear war.";

    public ChatCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        String chat = (String) packet.getObject(0);
        if (chat.startsWith("/")) {
            server.processCommand(connId, chat);
        } else if (packet.getData().length > 1) {
            connId = (int) packet.getObject(1);
            if (connId == Player.PLAYER_NONE) {
                server.sendServerChat(chat);
            } else {
                server.sendServerChat(connId, chat);
            }
        } else {
            server.sendChat(server.getGame().getPlayer(connId).getName(), chat);
        }
        // Easter eggs. Happy April Fool's Day!!
        if (DUNE_CALL.equalsIgnoreCase(chat)) {
            server.sendServerChat(DUNE_RESPONSE);
        } else if (STAR_WARS_CALL.equalsIgnoreCase(chat)) {
            server.sendServerChat(STAR_WARS_RESPONSE);
        } else if (INVADER_ZIM_CALL.equalsIgnoreCase(chat)) {
            server.sendServerChat(INVADER_ZIM_RESPONSE);
        } else if (WARGAMES_CALL.equalsIgnoreCase(chat)) {
            server.sendServerChat(WARGAMES_RESPONSE);
        }
    }
}
