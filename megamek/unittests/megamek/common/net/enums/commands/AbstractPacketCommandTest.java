package megamek.common.net.enums.commands;

import megamek.MMConstants;
import megamek.MegaMek;
import megamek.Version;
import megamek.common.Game;
import megamek.common.Player;
import megamek.common.enums.GamePhase;
import megamek.common.icons.Camouflage;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.leaderboard.ILeaderboardManager;
import megamek.server.GameManager;
import megamek.server.Server;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.net.ServerSocket;
import java.util.Collections;
import java.util.Vector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AbstractPacketCommandTest {

    private MockedStatic<Server> staticMock;

    private GameManager manager;
    private AbstractConnection conn;
    private Vector<Player> players;
    private Player player;
    private Game game;
    private ILeaderboardManager leaderboardManager;
    private ServerSocket socket;
    private Server server;

    protected void initServerSocket() {
        socket = Mockito.mock(ServerSocket.class);
        when(socket.getLocalPort()).thenReturn(7000);
    }

    protected void initLeaderboardManager() {
        leaderboardManager = Mockito.mock(ILeaderboardManager.class);
    }

    protected void initMockedPlayers() {
        players = new Vector<>();
        player = Mockito.mock(Player.class);
        when(player.isGhost()).thenReturn(true);
        when(player.getName()).thenReturn("John");
        when(player.getConstantInitBonus()).thenReturn(50);

        players.add(player);
    }

    protected void initGame() {
        initMockedPlayers();
        game = mock(Game.class);

        when(game.getGameListeners()).thenReturn(new Vector<>());
        when(game.getEntities()).thenReturn(Collections.emptyIterator());
        when(game.getEntitiesVector()).thenReturn(Collections.emptyList());
        when(game.getPlayers()).thenReturn(players.elements());
        when(game.getPhase()).thenReturn(GamePhase.MOVEMENT);
        when(game.getEntitiesOwnedBy(any(Player.class))).thenReturn(0);
        when(game.getPlayer(any(int.class))).thenReturn(player);
    }

    @AfterAll
    public void teardown() {
        staticMock.close();
    }

    @BeforeAll
    public void initStatic() {
        server = Mockito.mock(Server.class);
        staticMock = Mockito.mockStatic(Server.class);
        staticMock.when(Server::getServerInstance).thenReturn(server);
    }

    @BeforeEach
    public void init() {
        manager = Mockito.mock(GameManager.class);
        conn = Mockito.mock(AbstractConnection.class);
        when(conn.getInetAddress()).thenReturn("192.168.50.5/24");

        initServerSocket();
        initLeaderboardManager();
        initGame();

        when(server.getPendingConnection(any(int.class))).thenReturn(conn);
        when(server.getConnection(any(int.class))).thenReturn(conn);
        when(server.getGame()).thenReturn(game);
        when(server.getLeaderboardManager()).thenReturn(leaderboardManager);
        when(server.getPlayer(any(int.class))).thenReturn(player);
        when(server.getServerSocket()).thenReturn(socket);
        when(server.getConnections()).thenReturn(Collections.singletonList(conn));
    }

    @Test
    public void testClientNameCommand() {
        AbstractPacketCommand command = PacketCommand.CLIENT_NAME.getCommand(manager);
        command.handle(15, new Packet(PacketCommand.CLIENT_NAME, "John", false));
    }

    @Test
    public void testUnimplementedCommand() {
        AbstractPacketCommand command = new UnimplementedCommand(manager);
        command.handle(15, new Packet(PacketCommand.ILLEGAL_CLIENT_VERSION));
    }

    @Test
    public void testChatCommand() {
        AbstractPacketCommand command = PacketCommand.CHAT.getCommand(manager);
        command.handle(15, new Packet(PacketCommand.CHAT, "/command"));
        command.handle(15, new Packet(PacketCommand.CHAT, "/command", -1));
        command.handle(15, new Packet(PacketCommand.CHAT, "/command", 15));
        command.handle(15, new Packet(PacketCommand.CHAT, "test"));
        command.handle(15, new Packet(PacketCommand.CHAT, "They tried and failed?"));
        command.handle(15, new Packet(PacketCommand.CHAT, "I'd just as soon kiss a Wookiee."));
        command.handle(15, new Packet(PacketCommand.CHAT, "What does the G stand for?"));
        command.handle(15, new Packet(PacketCommand.CHAT, "Shall we play a game?"));
    }

    @Test
    public void testClientVersionsCommand() {
        AbstractPacketCommand command = PacketCommand.CLIENT_VERSIONS.getCommand(manager);
        Version badVersion = new Version();
        badVersion.setMajor(0);
        badVersion.setMinor(0);
        badVersion.setRelease(0);
        command.handle(15, new Packet(PacketCommand.CLIENT_VERSIONS, badVersion));
        command.handle(15, new Packet(PacketCommand.CLIENT_VERSIONS, MMConstants.VERSION));
        command.handle(15, new Packet(PacketCommand.CLIENT_VERSIONS, MMConstants.VERSION, MegaMek.getMegaMekSHA256()));
        command.handle(15, new Packet(PacketCommand.CLIENT_VERSIONS, MMConstants.VERSION, "bad-checksum"));
    }

    @Test
    public void testCloseConnectionsCommand() {
        AbstractPacketCommand command = PacketCommand.CLOSE_CONNECTION.getCommand(manager);
        command.handle(15, new Packet(PacketCommand.CLOSE_CONNECTION));
        when(server.getConnection(any(int.class))).thenReturn(null);
        command.handle(15, new Packet(PacketCommand.CLIENT_VERSIONS));
    }

    @Test
    public void testLoadGameCommand() {
        AbstractPacketCommand command = PacketCommand.LOAD_GAME.getCommand(manager);
        command.handle(15, new Packet(PacketCommand.LOAD_GAME, new Game()));
        command.handle(15, new Packet(PacketCommand.LOAD_GAME, "Exception"));
    }

    @Test
    public void testPlayerUpdateCommand() {
        Player gamePlayer = Mockito.mock(Player.class);
        when(gamePlayer.getConstantInitBonus()).thenReturn(49);
        when(game.getPlayer(any(int.class))).thenReturn(gamePlayer);
        when(player.getCamouflage()).thenReturn(new Camouflage());
        AbstractPacketCommand command = PacketCommand.PLAYER_UPDATE.getCommand(manager);
        command.handle(15, new Packet(PacketCommand.PLAYER_UPDATE, player));
    }
}
