package megamek.common.net.enums.commands;

import megamek.common.Player;
import megamek.common.net.packets.Packet;
import megamek.server.GameManager;

public class PlayerUpdateCommand extends AbstractPacketCommand{
    public PlayerUpdateCommand(GameManager manager) {
        super(manager);
    }

    @Override
    public void handle(int connId, Packet packet) {
        receivePlayerInfo(packet, connId);
        server.validatePlayerInfo(connId);
        server.transmitPlayerUpdate(server.getPlayer(connId));
    }

    /**
     * Allow the player to set whatever parameters he is able to
     */
    private void receivePlayerInfo(Packet packet, int connId) {
        Player player = (Player) packet.getObject(0);
        Player gamePlayer = server.getGame().getPlayer(connId);
        if (null != gamePlayer) {
            gamePlayer.setColour(player.getColour());
            gamePlayer.setStartingPos(player.getStartingPos());
            gamePlayer.setStartWidth(player.getStartWidth());
            gamePlayer.setStartOffset(player.getStartOffset());
            gamePlayer.setStartingAnyNWx(player.getStartingAnyNWx());
            gamePlayer.setStartingAnyNWy(player.getStartingAnyNWy());
            gamePlayer.setStartingAnySEx(player.getStartingAnySEx());
            gamePlayer.setStartingAnySEy(player.getStartingAnySEy());
            gamePlayer.setTeam(player.getTeam());
            gamePlayer.setCamouflage(player.getCamouflage().clone());
            gamePlayer.setNbrMFConventional(player.getNbrMFConventional());
            gamePlayer.setNbrMFCommand(player.getNbrMFCommand());
            gamePlayer.setNbrMFVibra(player.getNbrMFVibra());
            gamePlayer.setNbrMFActive(player.getNbrMFActive());
            gamePlayer.setNbrMFInferno(player.getNbrMFInferno());
            if (gamePlayer.getConstantInitBonus() != player.getConstantInitBonus()) {
                server.sendServerChat("Player " + gamePlayer.getName()
                        + " changed their initiative bonus from "
                        + gamePlayer.getConstantInitBonus() + " to "
                        + player.getConstantInitBonus() + '.');
            }
            gamePlayer.setConstantInitBonus(player.getConstantInitBonus());
            gamePlayer.setEmail(player.getEmail());
        }
    }
}
