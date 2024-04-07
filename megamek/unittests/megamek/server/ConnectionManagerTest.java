package megamek.server;

import megamek.common.Game;
import megamek.common.Player;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConnectionManagerTest {

    private ConnectionManager underTest;
    private AbstractConnection conn;
    private AbstractConnection conn2;
    private AbstractConnection conn3;
    private Server server;
    private Vector<Player> players;
    private Player player;
    private Player player2;
    private Game game;

    private Map<String, Integer> createNameToIdMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("John", 15);
        map.put("Joey", 1);
        return map;
    }

    private Map<Integer, String> createIdToNameMap() {
        Map<Integer, String> map = new HashMap<>();
        map.put(16, "John");
        map.put(3, "Turner");
        return map;
    }

    private void initPlayersVector() {
        players = new Vector<>();
        player = Mockito.mock(Player.class);
        player2 = Mockito.mock(Player.class);
        when(player.getName()).thenReturn("John");
        when(player.getId()).thenReturn(15);
        when(player2.getName()).thenReturn("Joey");
        when(player2.getId()).thenReturn(3);
        players.add(player);
        players.add(player2);
    }

    private void initGame() {
        game = Mockito.mock(Game.class);
        when(game.getPlayersVector()).thenReturn(players);
        when(game.getPlayer(3)).thenReturn(player2);
    }

    @BeforeAll
    public void setup() {
        underTest = ConnectionManager.getInstance();
        initPlayersVector();
        initGame();
        conn = Mockito.mock(AbstractConnection.class);
        conn2 = Mockito.mock(AbstractConnection.class);
        conn3 = Mockito.mock(AbstractConnection.class);
        when(conn.getId()).thenReturn(15);
        when(conn2.getId()).thenReturn(1);
        when(conn3.getId()).thenReturn(3);

        server = Mockito.mock(Server.class);
        when(server.getGame()).thenReturn(game);
    }

    @BeforeEach
    public void init() {
        underTest.clearConnections();
        underTest.clearConnectionIds();
        underTest.clearPendingConnections();
    }

    @Test
    public void testConnectionManagerConnectionHandling() {
        underTest.addConnection(conn);
        Assertions.assertNotNull(underTest.getConnection(15));
        Assertions.assertEquals(1, underTest.getConnections().size());

        underTest.removeConnection(conn);
        Assertions.assertEquals(0, underTest.getConnections().size());

        underTest.addConnection(conn);
        underTest.clearConnections();
    }

    @Test
    public void testConnectionManagerPendingConnectionAndConnectionHandlerHandling() {
        underTest.addPendingConnection(conn);
        Assertions.assertNotNull(underTest.getPendingConnection(15));
        underTest.removePendingConnection(conn);
        Assertions.assertNull(underTest.getPendingConnection(15));
        underTest.addPendingConnection(conn);
        underTest.clearPendingConnections();
        Assertions.assertNull(underTest.getPendingConnection(15));

        underTest.addConnectionHandler(15, new ConnectionHandler(conn));
        underTest.removeConnectionHandler(conn);
    }

    @Test
    public void testAuxiliaryFunctions() {
        when(server.addNewPlayer(any(int.class), any(String.class), any(Boolean.class))).thenReturn(Mockito.mock(Player.class));
        underTest.addConnection(conn);
        underTest.addConnection(conn2);
        underTest.addConnection(conn3);
        underTest.sendToAll(new Packet(PacketCommand.CLIENT_NAME));

        underTest.remapConnIds(createNameToIdMap(), createIdToNameMap(), server);
        underTest.forEachConnection(conn -> conn.send(new Packet(PacketCommand.CHAT)));
    }
}
