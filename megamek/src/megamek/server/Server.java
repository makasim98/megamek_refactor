/*
 * Copyright (c) 2000-2005 - Ben Mazur (bmazur@sev.org)
 * Copyright (c) 2013 - Edward Cullen (eddy@obsessedcomputers.co.uk)
 * Copyright (c) 2018-2022 - The MegaMek Team. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */
package megamek.server;

import com.thoughtworks.xstream.XStream;
import megamek.MMConstants;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.codeUtilities.StringUtility;
import megamek.common.*;
import megamek.common.annotations.Nullable;
import megamek.common.commandline.AbstractCommandLineParser.ParseException;
import megamek.common.icons.Camouflage;
import megamek.common.net.connections.AbstractConnection;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.enums.commands.AbstractPacketCommand;
import megamek.common.net.enums.commands.UnimplementedCommand;
import megamek.common.net.events.DisconnectedEvent;
import megamek.common.net.events.PacketReceivedEvent;
import megamek.common.net.factories.ConnectionFactory;
import megamek.common.net.listeners.ConnectionListener;
import megamek.common.net.packets.Packet;
import megamek.common.options.GameOptions;
import megamek.common.options.OptionsConstants;
import megamek.common.util.EmailService;
import megamek.common.util.SerializationHelper;
import megamek.leaderboard.*;
import megamek.leaderboard.ranking.RankingStrategy;
import megamek.leaderboard.storage.LeaderboardStorage;
import megamek.server.commands.ServerCommand;
import megamek.server.victory.VictoryResult;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * @author Ben Mazur
 */
public class Server implements Runnable {
    // server setup
    private final String password;

    private final IGameManager gameManager;

    private final ILeaderboardManager leaderboardManager = new LeaderboardManager(LeaderboardStorage.CSV, RankingStrategy.ELO);

    public ILeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    private final String metaServerUrl;

    private final ServerSocket serverSocket;

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    private final String motd;

    public String getMotd() {
        return motd;
    }

    private final EmailService mailer;

    private ConnectionManager connectionManager;

    public static class ReceivedPacket {
        private int connectionId;
        private Packet packet;

        public ReceivedPacket(int cid, Packet p) {
            setPacket(p);
            setConnectionId(cid);
        }

        public int getConnectionId() {
            return connectionId;
        }

        public void setConnectionId(int connectionId) {
            this.connectionId = connectionId;
        }

        public Packet getPacket() {
            return packet;
        }

        public void setPacket(Packet packet) {
            this.packet = packet;
        }
    }

    private class PacketPump implements Runnable {
        boolean shouldStop;

        PacketPump() {
            shouldStop = false;
        }

        void signalEnd() {
            shouldStop = true;
        }

        @Override
        public void run() {
            while (!shouldStop) {
                while (!packetQueue.isEmpty()) {
                    ReceivedPacket rp = packetQueue.poll();
                    synchronized (serverLock) {
                        handle(rp.getConnectionId(), rp.getPacket());
                    }
                }

                try {
                    synchronized (packetQueue) {
                        packetQueue.wait();
                    }
                } catch (InterruptedException ignored) {
                    // If we are interrupted, just keep going, generally
                    // this happens after we are signalled to stop.
                }
            }
        }
    }

    // commands
    private final Map<String, ServerCommand> commandsHash = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<Server.ReceivedPacket> packetQueue = new ConcurrentLinkedQueue<>();

    private final boolean dedicated;

    private int connectionCounter;

    // listens for and connects players
    private Thread connector;

    private final PacketPump packetPump;
    private Thread packetPumpThread;

    private final Timer watchdogTimer = new Timer("Watchdog Timer");

    private static Server serverInstance = null;

    private String serverAccessKey = null;

    private Timer serverBrowserUpdateTimer = null;

    /**
     * Used to ensure only one thread at a time is accessing this particular
     * instance of the server.
     */
    private final Object serverLock = new Object();

    public static final String ORIGIN = "***Server";

    private final ConnectionListener connectionListener = new ConnectionListener() {
        /**
         * Called when it is sensed that a connection has terminated.
         */
        @Override
        public void disconnected(DisconnectedEvent e) {
            AbstractConnection conn = e.getConnection();

            // write something in the log
            LogManager.getLogger().info("s: connection " + conn.getId() + " disconnected");

            synchronized (serverLock) {
                connectionManager.removePendingConnection(conn);
                connectionManager.removeConnection(conn);
                connectionManager.removeConnectionHandler(conn);
            }
            // if there's a player for this connection, remove it too
            Player player = getPlayer(conn.getId());
            if (null != player) {
                Server.this.disconnected(player);
            }
        }

        @Override
        public void packetReceived(PacketReceivedEvent e) {
            ReceivedPacket rp = new ReceivedPacket(e.getConnection().getId(), e.getPacket());
            switch (e.getPacket().getCommand()) {
                case CLIENT_FEEDBACK_REQUEST:
                    // Handled CFR packets specially
                    gameManager.handleCfrPacket(rp);
                    break;
                case CLOSE_CONNECTION:
                case CLIENT_NAME:
                case CLIENT_VERSIONS:
                case CHAT:
                    // Some packets should be handled immediately
                    handle(rp.getConnectionId(), rp.getPacket());
                    break;
                default:
                    synchronized (packetQueue) {
                        packetQueue.add(rp);
                        packetQueue.notifyAll();
                    }
                    break;
            }
        }
    };

    /**
     * @param serverAddress
     * @return valid hostName
     * @throws ParseException for null or empty serverAddress
     */
    public static String validateServerAddress(String serverAddress) throws ParseException {
        if ((serverAddress == null) || serverAddress.isBlank()) {
            String msg = "serverAddress must not be null or empty";
            LogManager.getLogger().error(msg);
            throw new ParseException(msg);
        } else {
            return serverAddress.trim();
        }
    }

    /**
     * @param playerName throw ParseException if null or empty
     * @return valid playerName
     */
    public static String validatePlayerName(String playerName) throws ParseException {
        if (playerName == null) {
            String msg = "playerName must not be null";
            LogManager.getLogger().error(msg);
            throw new ParseException(msg);
        } else if (playerName.isBlank()) {
            String msg = "playerName must not be empty string";
            LogManager.getLogger().error(msg);
            throw new ParseException(msg);
        } else {
            return playerName.trim();
        }
    }

    /**
     * @param password
     * @return valid password or null if no password or password is blank string
     */
    public static @Nullable String validatePassword(@Nullable String password) {
        return StringUtility.isNullOrBlank(password) ? null : password.trim();
    }

    /**
     * Checks a String against the server password
     *
     * @param password The password provided by the user.
     * @return true if the user-supplied data matches the server password or no password is set.
     */
    public boolean passwordMatches(Object password) {
        return StringUtility.isNullOrBlank(this.password) || this.password.equals(password);
    }

    /**
     * @param port if 0 or less, will return default, if illegal number, throws ParseException
     * @return valid port number
     */
    public static int validatePort(int port) throws ParseException {
        if (port <= 0) {
            return MMConstants.DEFAULT_PORT;
        } else if ((port < MMConstants.MIN_PORT) || (port > MMConstants.MAX_PORT)) {
            String msg = String.format("Port number %d outside allowed range %d-%d", port, MMConstants.MIN_PORT, MMConstants.MAX_PORT);
            LogManager.getLogger().error(msg);
            throw new ParseException(msg);
        } else {
            return port;
        }
    }

    public Server(@Nullable String password, int port, IGameManager gameManager) throws IOException {
        this(password, port, gameManager, false, "", null, false);
    }

    public Server(@Nullable String password, int port, IGameManager gameManager,
                  boolean registerWithServerBrowser, @Nullable String metaServerUrl) throws IOException {
        this(password, port, gameManager, registerWithServerBrowser, metaServerUrl, null, false);
    }

    /**
     * Construct a new GameHost and begin listening for incoming clients.
     *
     * @param password                  the <code>String</code> that is set as a password
     * @param port                      the <code>int</code> value that specifies the port that is
     *                                  used
     * @param gameManager               the {@link IGameManager} instance for this server instance.
     * @param registerWithServerBrowser a <code>boolean</code> indicating whether we should register
     *                                  with the master server browser on MegaMek.info
     * @param mailer                    an email service instance to use for sending round reports.
     * @param dedicated                 set to true if this server is started from a GUI-less context
     */
    public Server(@Nullable String password, int port, IGameManager gameManager,
                  boolean registerWithServerBrowser, @Nullable String metaServerUrl,
                  @Nullable EmailService mailer, boolean dedicated) throws IOException {
        this.metaServerUrl = StringUtility.isNullOrBlank(metaServerUrl) ? null : metaServerUrl;
        this.password = StringUtility.isNullOrBlank(password) ? null : password;
        this.gameManager = gameManager;
        this.mailer = mailer;
        this.dedicated = dedicated;

        // initialize server socket
        serverSocket = new ServerSocket(port);
        connectionManager = ConnectionManager.getInstance();

        motd = createMotd();

        // display server start text
        LogManager.getLogger().info("s: starting a new server...");

        try {
            StringBuilder sb = new StringBuilder();
            String host = InetAddress.getLocalHost().getHostName();
            sb.append("s: hostname = '");
            sb.append(host);
            sb.append("' port = ");
            sb.append(serverSocket.getLocalPort());
            sb.append('\n');
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                sb.append("s: hosting on address = ");
                sb.append(address.getHostAddress());
                sb.append('\n');
            }

            LogManager.getLogger().info(sb.toString());
        } catch (Exception ignored) {

        }

        LogManager.getLogger().info("s: password = " + this.password);

        for (ServerCommand command : gameManager.getCommandList(this)) {
            registerCommand(command);
        }

        packetPump = new PacketPump();
        packetPumpThread = new Thread(packetPump, "Packet Pump");
        packetPumpThread.start();

        if (registerWithServerBrowser) {
            if (!StringUtility.isNullOrBlank(metaServerUrl)) {
                final TimerTask register = new TimerTask() {
                    @Override
                    public void run() {
                        registerWithServerBrowser(true, Server.getServerInstance().metaServerUrl);
                    }
                };
                serverBrowserUpdateTimer = new Timer("Server Browser Register Timer", true);
                serverBrowserUpdateTimer.schedule(register, 1, 40000);
            } else {
                LogManager.getLogger().error("Invalid URL for server browser " + this.metaServerUrl);
            }
        }

        // Fully initialised, now accept connections
        connector = new Thread(this, "Connection Listener");
        connector.start();

        serverInstance = this;
    }

    public IGameManager getGameManager() {
        return gameManager;
    }

    /**
     * Sets the game for this server. Restores any transient fields, and sets
     * all players as ghosts. This should only be called during server
     * initialization before any players have connected.
     */
    public void setGame(IGame g) {
        gameManager.setGame(g);
    }

    public IGame getGame() {
        return gameManager.getGame();
    }

    public EmailService getEmailService() {
        return mailer;
    }

    /**
     * Make a default message o' the day containing the version string, and if
     * it was found, the build timestamp
     */
    private String createMotd() {
        return "Welcome to MegaMek. Server is running version " + MMConstants.VERSION;
    }

    /**
     * @return true if the server has a password
     */
    public boolean isPassworded() {
        return password != null;
    }

    /**
     * @return true if the password matches
     */
    public boolean isPassword(Object guess) {
        return password.equals(guess);
    }

    /**
     * Registers a new command in the server command table
     */
    private void registerCommand(ServerCommand command) {
        commandsHash.put(command.getName(), command);
    }

    /**
     * Returns the command associated with the specified name
     */
    public ServerCommand getCommand(String name) {
        return commandsHash.get(name);
    }

    /**
     * @return true run from a GUI-less context
     */
    public boolean getDedicated() {
        return dedicated;
    }

    /**
     * Shuts down the server.
     */
    public void die() {
        watchdogTimer.cancel();

        // kill thread accepting new connections
        connector = null;
        packetPump.signalEnd();
        packetPumpThread.interrupt();
        packetPumpThread = null;

        // close socket
        try {
            serverSocket.close();
        } catch (Exception ignored) {

        }

        connectionManager.clearPendingConnections();

        // Send "kill" commands to all connections
        // This WILL handle the connection end on both sides
        sendToAll(new Packet(PacketCommand.CLOSE_CONNECTION));
        connectionManager.clearConnectionIds();

        // Shutdown Email
        if (mailer != null) {
            mailer.shutdown();
        }

        // Unregister Server Browser Setup
        if (serverBrowserUpdateTimer != null) {
            serverBrowserUpdateTimer.cancel();
        }

        if ((metaServerUrl != null) && (!metaServerUrl.isBlank())) {
            registerWithServerBrowser(false, metaServerUrl);
        }
    }

    /**
     * Returns an enumeration of all the command names
     */
    public Collection<String> getAllCommandNames() {
        return commandsHash.keySet();
    }

    /**
     * Sent when a client attempts to connect.
     */
    void clientVersionCheck(int cn) {
        sendToPending(cn, new Packet(PacketCommand.SERVER_VERSION_CHECK));
    }

    /**
     * Returns a free connection id.
     */
    public int getFreeConnectionId() {
        while ((connectionManager.getPendingConnection(connectionCounter) != null)
                || (connectionManager.getConnection(connectionCounter) != null)
                || (getPlayer(connectionCounter) != null)) {
            connectionCounter++;
        }
        return connectionCounter;
    }

    /**
     * Returns a free entity id. Perhaps this should be in Game instead.
     */
    public int getFreeEntityId() {
        return getGame().getNextEntityId();
    }

    /**
     * Sends a player the info they need to look at the current phase. This is
     * triggered when a player first connects to the server.
     */
    public void sendCurrentInfo(int connId) {
        transmitPlayerConnect(connectionManager.getConnection(connId));
        gameManager.sendCurrentInfo(connId);
    }

    /**
     * Adds a new player to the game
     */
    public Player addNewPlayer(int connId, String name, boolean isBot) {
        int team = Player.TEAM_UNASSIGNED;
        if (getGame().getPhase().isLounge()) {
            team = Player.TEAM_NONE;
            final GameOptions gOpts = getGame().getOptions();
            if (isBot || !gOpts.booleanOption(OptionsConstants.BASE_SET_DEFAULT_TEAM_1)) {
                for (Player p : getGame().getPlayersList()) {
                    if (p.getTeam() > team) {
                        team = p.getTeam();
                    }
                }
                team++;
            } else {
                team = 1;
            }

        }
        Player newPlayer = new Player(connId, name);
        newPlayer.setBot(isBot);
        PlayerColour colour = newPlayer.getColour();
        Enumeration<Player> players = getGame().getPlayers();
        final PlayerColour[] colours = PlayerColour.values();
        while (players.hasMoreElements()) {
            final Player p = players.nextElement();
            if (p.getId() == newPlayer.getId()) {
                continue;
            }

            if ((p.getColour() == colour) && (colours.length > (colour.ordinal() + 1))) {
                colour = colours[colour.ordinal() + 1];
            }
        }
        newPlayer.setColour(colour);
        newPlayer.setCamouflage(new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, colour.name()));
        newPlayer.setTeam(Math.min(team, 5));
        getGame().addPlayer(connId, newPlayer);
        validatePlayerInfo(connId);
        return newPlayer;
    }

    /**
     * Validates the player info.
     */
    public void validatePlayerInfo(int playerId) {
        final Player player = getPlayer(playerId);

        if (player != null) {
            // TODO : check for duplicate or reserved names

            // Colour Assignment
            final PlayerColour[] playerColours = PlayerColour.values();
            boolean allUsed = true;
            Set<PlayerColour> colourUtilization = new HashSet<>();
            for (Enumeration<Player> i = getGame().getPlayers(); i.hasMoreElements(); ) {
                final Player otherPlayer = i.nextElement();
                if (otherPlayer.getId() != playerId) {
                    colourUtilization.add(otherPlayer.getColour());
                } else {
                    allUsed = false;
                }
            }

            if (!allUsed && colourUtilization.contains(player.getColour())) {
                for (PlayerColour colour : playerColours) {
                    if (!colourUtilization.contains(colour)) {
                        player.setColour(colour);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Called when it's been determined that an actual player disconnected.
     * Notifies the other players and does the appropriate housekeeping.
     */
    void disconnected(Player player) {
        gameManager.disconnect(player);
    }

    public void resetGame() {
        gameManager.resetGame();
    }

    /**
     * send a packet to the connection tells it load a locally saved game
     *
     * @param connId The <code>int</code> connection id to send to
     * @param sFile  The <code>String</code> filename to use
     */
    public void sendLoadGame(int connId, String sFile) {
        String sFinalFile = sFile;
        if (!sFinalFile.endsWith(MMConstants.SAVE_FILE_EXT) && !sFinalFile.endsWith(MMConstants.SAVE_FILE_GZ_EXT)) {
            sFinalFile = sFile + MMConstants.SAVE_FILE_EXT;
        }
        if (!sFinalFile.endsWith(".gz")) {
            sFinalFile = sFinalFile + ".gz";
        }
        send(connId, new Packet(PacketCommand.LOAD_SAVEGAME, sFinalFile));
    }

    /**
     * load the game
     *
     * @param f The <code>File</code> to load
     * @return A <code>boolean</code> value whether or not the loading was successful
     */
    public boolean loadGame(File f) {
        return loadGame(f, true);
    }

    /**
     * load the game
     *
     * @param f        The <code>File</code> to load
     * @param sendInfo Determines whether the connections should be updated with
     *                 current info. This may be false if some reconnection remapping
     *                 needs to be done first.
     * @return A <code>boolean</code> value whether or not the loading was successful
     */
    public boolean loadGame(File f, boolean sendInfo) {
        LogManager.getLogger().info("s: loading saved game file '" + f.getAbsolutePath() + '\'');

        Game newGame;
        try (InputStream is = new FileInputStream(f)) {
            InputStream gzi;

            if (f.getName().toLowerCase().endsWith(".gz")) {
                gzi = new GZIPInputStream(is);
            } else {
                gzi = is;
            }

            XStream xstream = SerializationHelper.getLoadSaveGameXStream();
            newGame = (Game) xstream.fromXML(gzi);
        } catch (Exception e) {
            LogManager.getLogger().error("Unable to load file: " + f, e);
            return false;
        }

        setGame(newGame);

        if (!sendInfo) {
            return true;
        }

        // update all the clients with the new game info
        for (AbstractConnection conn : connectionManager.getConnections()) {
            sendCurrentInfo(conn.getId());
        }
        return true;
    }

    public void saveGame(String fileName) {
        gameManager.saveGame(fileName);
    }

    public void sendSaveGame(int connId, String fileName, String localPath) {
        gameManager.sendSaveGame(connId, fileName, localPath);
    }

    /**
     * When the load command is used, there is a list of already connected
     * players which have assigned names and player id numbers with the id
     * numbers matching the connection numbers. When a new game is loaded, this
     * mapping may need to be updated. This method takes a map of player names
     * to their current ids, and uses the list of players to figure out what the
     * current ids should change to.
     *
     * @param nameToIdMap This maps a player name to the current connection ID
     * @param idToNameMap This maps a current conn ID to a player name, and is just the
     *                    inverse mapping from nameToIdMap
     */
    public void remapConnIds(Map<String, Integer> nameToIdMap, Map<Integer, String> idToNameMap) {
        connectionManager.remapConnIds(nameToIdMap, idToNameMap, this);
    }

    /**
     * Shortcut to game.getPlayer(id)
     */
    public Player getPlayer(int id) {
        return getGame().getPlayer(id);
    }

    /**
     * Sends out all player info to the specified connection
     */
    private void transmitPlayerConnect(AbstractConnection connection) {
        for (var player : getGame().getPlayersVector()) {
            var connectionId = connection.getId();
            connection.send(createPlayerConnectPacket(player, player.getId() != connectionId));
        }
    }

    /**
     * Creates a packet informing that the player has connected
     */
    public Packet createPlayerConnectPacket(Player player, boolean isPrivate) {
        var playerId = player.getId();
        var destPlayer = player;
        if (isPrivate) {
            // Sending the player's data to another player's
            // connection, need to redact any private data
            destPlayer = player.copy();
            destPlayer.redactPrivateData();
        }
        return new Packet(PacketCommand.PLAYER_ADD, playerId, destPlayer);
    }

    /**
     * Sends out player info updates for a player to all connections
     */
    public void transmitPlayerUpdate(Player player) {
        for (var connection : connectionManager.getConnections()) {
            var playerId = player.getId();
            var destPlayer = player;

            if (playerId != connection.getId()) {
                // Sending the player's data to another player's
                // connection, need to redact any private data
                destPlayer = player.copy();
                destPlayer.redactPrivateData();
            }
            connection.send(new Packet(PacketCommand.PLAYER_UPDATE, playerId, destPlayer));
        }
    }

    public void requestTeamChange(int teamId, Player player) {
        gameManager.requestTeamChange(teamId, player);
    }

    public void requestGameMaster(Player player) {
        gameManager.requestGameMaster(player);
    }

    public static String formatChatMessage(String origin, String message) {
        return origin + ": " + message;
    }

    /**
     * Transmits a chat message to all players
     */
    public void sendChat(int connId, String origin, String message) {
        send(connId, new Packet(PacketCommand.CHAT, formatChatMessage(origin, message)));
    }

    /**
     * Transmits a chat message to all players
     */
    public void sendChat(String origin, String message) {
        sendToAll(new Packet(PacketCommand.CHAT, formatChatMessage(origin, message)));
    }

    public void sendServerChat(int connId, String message) {
        sendChat(connId, ORIGIN, message);
    }

    public void sendServerChat(String message) {
        sendChat(ORIGIN, message);
    }

    void sendToAll(Packet packet) {
        connectionManager.sendToAll(packet);
    }

    /**
     * Send a packet to a specific connection.
     */
    public void send(int connId, Packet packet) {
        AbstractConnection connection = connectionManager.getConnection(connId);
        if (connection != null) {
            connection.send(packet);
        }
        // What should we do if we've lost this client?
        // For now, nothing.
    }

    /**
     * Send a packet to a pending connection
     */
    public void sendToPending(int connId, Packet packet) {
        AbstractConnection pendingConn = connectionManager.getPendingConnection(connId);
        if (pendingConn != null) {
            pendingConn.send(packet);
        }
        // What should we do if we've lost this client?
        // For now, nothing.
    }

    /**
     * Process an in-game command
     */
    public void processCommand(int connId, String commandString) {
        // all tokens are read as strings; if they're numbers, string-ize 'em.
        // replaced the tokenizer with the split function.
        String[] args = commandString.split("\\s+");

        // figure out which command this is
        String commandName = args[0].substring(1);

        // process it
        ServerCommand command = getCommand(commandName);
        if (command != null) {
            command.run(connId, args);
        } else {
            sendServerChat(connId, "Command not recognized. Type /help for a list of commands.");
        }
    }

    /**
     * Process a packet from a connection.
     *
     * @param connId - the <code>int</code> ID the connection that received the
     *               packet.
     * @param packet - the <code>Packet</code> to be processed.
     */
    protected void handle(int connId, Packet packet) {
        Player player = getGame().getPlayer(connId);
        // Check player. Please note, the connection may be pending.
        if ((null == player) && (null == connectionManager.getPendingConnection(connId))) {
            LogManager.getLogger().error("Server does not recognize player at connection " + connId);
            return;
        }

        if (packet == null) {
            LogManager.getLogger().error("Got null packet");
            return;
        }
        AbstractPacketCommand command = packet.getCommand().getCommand((GameManager) gameManager);
        if (command instanceof UnimplementedCommand) {
            gameManager.handlePacket(connId, packet);
        }
        else {
            command.handle(connId, packet);
        }
    }

    /**
     * Listen for incoming clients.
     */
    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        LogManager.getLogger().info("s: listening for clients...");
        while (connector == currentThread) {
            try {
                Socket s = serverSocket.accept();
                synchronized (serverLock) {
                    int id = getFreeConnectionId();
                    LogManager.getLogger().info("s: accepting player connection #" + id + "...");

                    AbstractConnection c = ConnectionFactory.getInstance().createServerConnection(s, id);
                    c.addConnectionListener(connectionListener);
                    c.open();
                    connectionManager.addPendingConnection(c);
                    ConnectionHandler ch = new ConnectionHandler(c);
                    Thread newConnThread = new Thread(ch, "Connection " + id);
                    newConnThread.start();
                    connectionManager.addConnectionHandler(id, ch);

                    clientVersionCheck(id);
                    ConnectionWatchdog w = new ConnectionWatchdog(this, id);
                    watchdogTimer.schedule(w, 1000, 500);
                }
            } catch (Exception ignored) {

            }
        }
    }

    /**
     * @return a <code>String</code> representing the hostname
     */
    public String getHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            LogManager.getLogger().error("", ex);
            return "";
        }
    }

    /**
     * @return the <code>int</code> this server is listening on
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * @return the current server instance. This may be null if a server has not been started
     */
    public static @Nullable Server getServerInstance() {
        return serverInstance;
    }

    private void registerWithServerBrowser(boolean register, String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream();
                 DataOutputStream dos = new DataOutputStream(os)) {
                String content = "port=" + URLEncoder.encode(Integer.toString(serverSocket.getLocalPort()), StandardCharsets.UTF_8);
                if (register) {
                    for (AbstractConnection iconn : connectionManager.getConnections()) {
                        content += "&players[]=" + getPlayer(iconn.getId()).getName();
                    }

                    if (!getGame().getPhase().isLounge() && !getGame().getPhase().isUnknown()) {
                        content += "&close=yes";
                    }
                    content += "&version=" + MMConstants.VERSION;
                    if (isPassworded()) {
                        content += "&pw=yes";
                    }
                } else {
                    content += "&delete=yes";
                }

                if (serverAccessKey != null) {
                    content += "&key=" + serverAccessKey;
                }
                dos.writeBytes(content);
                dos.flush();

                try (InputStream is = conn.getInputStream();
                     InputStreamReader isr = new InputStreamReader(is);
                     BufferedReader br = new BufferedReader(isr)) {
                    String line;
                    if (conn.getResponseCode() == 200) {
                        while ((line = br.readLine()) != null) {
                            if (serverAccessKey == null) {
                                serverAccessKey = line;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {

        }
    }

    public void reportRoll(Roll roll) {
        Report r = new Report(1230);
        r.add(roll.getReport());
        gameManager.addReport(r);
    }

    public void updatePlayerRankings(VictoryResult vr) {
        List<PlayerStats> updatedRankings = leaderboardManager.updateRankings(vr);
        sendToAll(new Packet(PacketCommand.LEADERBOARD_UPDATE, updatedRankings));
    }

    public void addConnection(AbstractConnection conn) {
        connectionManager.addConnection(conn);
    }

    public void removePendingConnection(AbstractConnection conn) {
        connectionManager.removePendingConnection(conn);
    }

    public AbstractConnection getConnection(int id) {
        return connectionManager.getConnection(id);
    }

    public AbstractConnection getPendingConnection(int id) {
        return connectionManager.getPendingConnection(id);
    }

    public List<AbstractConnection> getConnections() {
        return connectionManager.getConnections();
    }

    public void forEachConnection(Consumer<AbstractConnection> process) {
        connectionManager.forEachConnection(process);
    }
}
