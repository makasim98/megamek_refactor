/*
 * Copyright (c) 2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package megamek.client.ui.swing.lobby;

import megamek.MMConstants;
import megamek.client.ui.Messages;
import megamek.client.ui.swing.ClientGUI;
import megamek.client.ui.swing.TableColumnManager;
import megamek.client.ui.swing.util.UIUtil;
import megamek.common.Player;
import megamek.leaderboard.PlayerStats;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static megamek.client.ui.swing.util.UIUtil.FONT_SCALE1;
import static megamek.client.ui.swing.util.UIUtil.scaleForGUI;

/**
 * A JPanel that holds a table giving an overview of the current player ranking on the server.
 * The table does not listen to game changes and requires being notified
 * through {@link #refreshData()}. It accesses data through the stored ClientGUI.
 */
public class LeaderboardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private enum TOMCOLS { NAME, WIN, LOSS, RANKING }
    private final LeaderboardModel leaderboardModel = new LeaderboardModel();
    private final JTable leaderboardTable = new JTable(leaderboardModel);
    private final TableColumnManager leaderboardManager = new TableColumnManager(leaderboardTable, false);
    private final JScrollPane scrLeaderboard = new JScrollPane(leaderboardTable);
    private final ClientGUI clientGui;
    private int shownColumn;

    /** Constructs the team overview panel; the given ClientGUI is used to access the game data. */
    public LeaderboardPanel(ClientGUI cg) {
        clientGui = cg;
        setLayout(new GridLayout(1, 1));
        leaderboardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leaderboardTable.getSelectionModel().addListSelectionListener(e -> repaint());
        leaderboardTable.getTableHeader().setReorderingAllowed(false);
        var colModel = leaderboardTable.getColumnModel();
        var centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        colModel.getColumn(TOMCOLS.RANKING.ordinal()).setCellRenderer(centerRenderer);
        colModel.getColumn(TOMCOLS.NAME.ordinal()).setCellRenderer(centerRenderer);
        colModel.getColumn(TOMCOLS.WIN.ordinal()).setCellRenderer(centerRenderer);
        colModel.getColumn(TOMCOLS.LOSS.ordinal()).setCellRenderer(centerRenderer);
        scrLeaderboard.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrLeaderboard);

        refreshData();
    }

    /** Refreshes the headers, setting the header names and gui scaling them. */
    public void refreshTableHeader() {
        JTableHeader header = leaderboardTable.getTableHeader();
        for (int i = 0; i < leaderboardTable.getColumnCount(); i++) {
            TableColumn column = leaderboardTable.getColumnModel().getColumn(i);
            column.setHeaderValue(leaderboardModel.getColumnName(i));
        }
        header.repaint();
    }

    /** Updates the table with data from the game. */
    public void refreshData() {
        // Update the data
        leaderboardModel.updateTable(clientGui.getClient().getRankings());
    }

    /** The table model for the Team overview panel */
    private class LeaderboardModel extends AbstractTableModel {
        private static final long serialVersionUID = 2747614890129092912L;

        private ArrayList<String> names = new ArrayList<>();
        private ArrayList<Integer> ranks = new ArrayList<>();
        private ArrayList<Integer> wins = new ArrayList<>();
        private ArrayList<Integer> loss = new ArrayList<>();

        @Override
        public int getRowCount() {
            return names.size();
        }

        public void clearData() {
            names.clear();
            ranks.clear();
            wins.clear();
            loss.clear();
        }

        @Override
        public int getColumnCount() {
            return TOMCOLS.values().length;
        }

        /** Updates the stored data from the provided game. */
        public void updateTable(List<PlayerStats> rankings) {
            clearData();
            Collections.sort(rankings, Comparator.comparing(PlayerStats::getRanking).reversed());
            for (PlayerStats stats : rankings) {
                names.add(stats.getName());
                ranks.add(stats.getRanking());
                wins.add(stats.getWins());
                loss.add(stats.getLoss());
            }
            leaderboardTable.clearSelection();
            fireTableDataChanged();
            updateRowHeights();
        }

        /** Finds and sets the required row height (max height of all cells plus margin). */
        private void updateRowHeights()
        {
            int rowHeight = 0;
            for (int row = 0; row < leaderboardTable.getRowCount(); row++)
            {
                for (int col = 0; col < leaderboardTable.getColumnCount(); col++) {
                    // Consider the preferred height of the team members column
                    TableCellRenderer renderer = leaderboardTable.getCellRenderer(row, col);
                    Component comp = leaderboardTable.prepareRenderer(renderer, row, col);
                    rowHeight = Math.max(rowHeight, comp.getPreferredSize().height);
                }
            }
            // Add a little margin to the rows
            leaderboardTable.setRowHeight(rowHeight + scaleForGUI(20));
        }

        @Override
        public String getColumnName(int column) {
            String text = Messages.getString("ChatLounge.leaderboard.COL" + TOMCOLS.values()[column]);
            float textSizeDelta = 0.3f;
            return "<HTML><NOBR>" + UIUtil.guiScaledFontHTML(textSizeDelta) + text;
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        @Override
        public Object getValueAt(int row, int col) {
            TOMCOLS column = TOMCOLS.values()[col];
            switch (column) {
                case NAME:
                    return names.get(row);

                case RANKING:
                    return ranks.get(row);

                case WIN:
                    return wins.get(row);

                case LOSS:
                    return loss.get(row);

                default:
                    return "NoContent";
            }
        }
    }

    /** A specialized renderer for the mek table. */
    private class MemberListRenderer extends JPanel implements TableCellRenderer {
        private static final long serialVersionUID = 6379065972840999336L;

        MemberListRenderer() {
            super();
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            if (!(value instanceof List<?>)) {
                return null;
            }
            removeAll();
            add(Box.createVerticalGlue());
            List<?> playerList = (List<?>) value;
            int size = scaleForGUI(2 * FONT_SCALE1);
            Font font = new Font(MMConstants.FONT_DIALOG, Font.PLAIN, scaleForGUI(FONT_SCALE1));
            for (Object obj : playerList) {
                if (!(obj instanceof Player)) {
                    continue;
                }
                Player player = (Player) obj;
                JLabel lblPlayer = new JLabel(player.getName());
                lblPlayer.setBorder(new EmptyBorder(3, 3, 3, 3));
                lblPlayer.setFont(font);
                lblPlayer.setIconTextGap(scaleForGUI(5));
                Image camo = player.getCamouflage().getImage();
                lblPlayer.setIcon(new ImageIcon(camo.getScaledInstance(-1, size, Image.SCALE_SMOOTH)));
                add(lblPlayer);
            }
            add(Box.createVerticalGlue());

            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            return this;
        }
    }

}