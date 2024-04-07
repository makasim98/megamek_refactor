package megamek.server;

import megamek.common.Player;
import megamek.common.annotations.Nullable;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ConnectionManager {

    private static ConnectionManager instance;

    private static final List<AbstractConnection> connections = new CopyOnWriteArrayList<>();
    private static final Map<Integer, AbstractConnection> connectionIds = new ConcurrentHashMap<>();
    private static final Map<Integer, ConnectionHandler> connectionHandlers = new ConcurrentHashMap<>();
    private static final List<AbstractConnection> connectionsPending = new CopyOnWriteArrayList<>();

    private ConnectionManager() {}

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void addConnection(AbstractConnection conn) {
        connections.add(conn);
        connectionIds.put(conn.getId(), conn);
    }

    public AbstractConnection getConnection(int connId) {
        return connectionIds.get(connId);
    }

    public void removeConnection(AbstractConnection conn) {
        connections.remove(conn);
        connectionIds.remove(conn.getId());
    }

    public void clearConnections() {
        connections.clear();
    }

    public void clearConnectionIds() {
        connectionIds.clear();
    }

    public List<AbstractConnection> getConnections() {
        return connections;
    }

    public void addPendingConnection(AbstractConnection conn) {
        connectionsPending.add(conn);
    }

    public @Nullable AbstractConnection getPendingConnection(int connId) {
        for (AbstractConnection conn : connectionsPending) {
            if (conn.getId() == connId) {
                return conn;
            }
        }
        return null;
    }

    public void removePendingConnection(AbstractConnection conn) {
        connectionsPending.remove(conn);
    }

    public void clearPendingConnections() {
        connectionsPending.forEach(AbstractConnection::close);
        connectionsPending.clear();
    }

    public void addConnectionHandler(int id, ConnectionHandler handler) {
        connectionHandlers.put(id, handler);
    }

    public void removeConnectionHandler(AbstractConnection conn) {
        ConnectionHandler ch = connectionHandlers.get(conn.getId());
        if (ch != null) {
            ch.signalStop();
            connectionHandlers.remove(conn.getId());
        }
    }

    public void forEachConnection(Consumer<AbstractConnection> process) {
        connections.forEach(process);
    }

    public void remapConnIds(Map<String, Integer> nameToIdMap, Map<Integer, String> idToNameMap, Server server) {
        // Keeps track of connections without Ids
        List<AbstractConnection> unassignedConns = new ArrayList<>();
        // Keep track of which ids are used
        Set<Integer> usedPlayerIds = new HashSet<>();
        Set<String> currentPlayerNames = new HashSet<>();
        for (Player p : server.getGame().getPlayersVector()) {
            currentPlayerNames.add(p.getName());
        }
        // Map the old connection Id to new value
        Map<Integer, Integer> connIdRemapping = new HashMap<>();
        for (Player p : server.getGame().getPlayersVector()) {
            // Check to see if this player was already connected
            Integer oldId = nameToIdMap.get(p.getName());
            if ((oldId != null) && (oldId != p.getId())) {
                connIdRemapping.put(oldId, p.getId());
            }
            // If the old and new Ids match, make sure we remove ghost status
            if ((oldId != null) && (oldId == p.getId())) {
                p.setGhost(false);
            }
            // Check to see if this player's Id is taken
            String oldName = idToNameMap.get(p.getId());
            if ((oldName != null) && !oldName.equals(p.getName())) {
                // If this name doesn't belong to a current player, unassign it
                if (!currentPlayerNames.contains(oldName)) {
                    unassignedConns.add(connectionIds.get(p.getId()));
                    // Make sure we don't add this to unassigned connections twice
                    connectionIds.remove(p.getId());
                }
                // If it does belong to a current player, it'll get handled
                // when that player comes up
            }
            // Keep track of what Ids are used
            usedPlayerIds.add(p.getId());
        }

        // Remap old connection Ids to new ones
        for (Integer currConnId : connIdRemapping.keySet()) {
            Integer newId = connIdRemapping.get(currConnId);
            AbstractConnection conn = connectionIds.get(currConnId);
            conn.setId(newId);
            // If this Id is used, make sure we reassign that connection
            if (connectionIds.containsKey(newId)) {
                unassignedConns.add(connectionIds.get(newId));
            }
            // Map the new Id
            connectionIds.put(newId, conn);

            server.getGame().getPlayer(newId).setGhost(false);
            server.send(newId, new Packet(PacketCommand.LOCAL_PN, newId));
        }

        // It's possible we have players not in the saved game, add 'em
        for (AbstractConnection conn : unassignedConns) {
            int newId = 0;
            while (usedPlayerIds.contains(newId)) {
                newId++;
            }
            String name = idToNameMap.get(conn.getId());
            conn.setId(newId);
            Player newPlayer = server.addNewPlayer(newId, name, false);
            newPlayer.setObserver(true);
            connectionIds.put(newId, conn);
            server.send(newId, new Packet(PacketCommand.LOCAL_PN, newId));
        }

        // Ensure all clients are up-to-date on player info
        transmitAllPlayerUpdates(server);
    }

    public void sendToAll(Packet packet) {
        connections.stream()
                .filter(Objects::nonNull)
                .forEach(connection -> connection.send(packet));
    }

    private void transmitAllPlayerUpdates(Server server) {
        for (var player : server.getGame().getPlayersVector()) {
            server.transmitPlayerUpdate(player);
        }
    }
}
