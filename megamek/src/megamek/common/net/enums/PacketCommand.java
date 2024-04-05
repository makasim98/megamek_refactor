/*
 * Copyright (c) 2022 - The MegaMek Team. All Rights Reserved.
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
package megamek.common.net.enums;

import megamek.common.net.enums.commands.*;
import megamek.server.GameManager;

import java.lang.reflect.InvocationTargetException;

public enum PacketCommand {
    //region Enum Declarations
    CLOSE_CONNECTION(CloseConnectionCommand.class),
    SERVER_VERSION_CHECK(UnimplementedCommand.class),
    SERVER_GREETING(UnimplementedCommand.class),
    ILLEGAL_CLIENT_VERSION(UnimplementedCommand.class),
    CLIENT_NAME(ClientNameCommand.class),
    CLIENT_VERSIONS(ClientVersionsCommand.class),
    LOCAL_PN(UnimplementedCommand.class),
    LEADERBOARD_UPDATE(UnimplementedCommand.class),
    PLAYER_ADD(UnimplementedCommand.class),
    PLAYER_REMOVE(UnimplementedCommand.class),
    PLAYER_UPDATE(PlayerUpdateCommand.class),
    PLAYER_TEAM_CHANGE(UnimplementedCommand.class),
    PRINCESS_SETTINGS(UnimplementedCommand.class),
    PLAYER_READY(UnimplementedCommand.class),
    CHAT(ChatCommand.class),
    ENTITY_ADD(UnimplementedCommand.class),
    ENTITY_REMOVE(UnimplementedCommand.class),
    ENTITY_MOVE(UnimplementedCommand.class),
    ENTITY_DEPLOY(UnimplementedCommand.class),
    ENTITY_DEPLOY_UNLOAD(UnimplementedCommand.class),
    ENTITY_ATTACK(UnimplementedCommand.class),
    ENTITY_PREPHASE(UnimplementedCommand.class),
    ENTITY_GTA_HEX_SELECT(UnimplementedCommand.class),
    ENTITY_UPDATE(UnimplementedCommand.class),
    ENTITY_MULTIUPDATE(UnimplementedCommand.class),
    ENTITY_WORDER_UPDATE(UnimplementedCommand.class),
    ENTITY_ASSIGN(UnimplementedCommand.class),
    ENTITY_MODECHANGE(UnimplementedCommand.class),
    ENTITY_AMMOCHANGE(UnimplementedCommand.class),
    ENTITY_SENSORCHANGE(UnimplementedCommand.class),
    ENTITY_SINKSCHANGE(UnimplementedCommand.class),
    ENTITY_ACTIVATE_HIDDEN(UnimplementedCommand.class),
    ENTITY_SYSTEMMODECHANGE(UnimplementedCommand.class),
    FORCE_UPDATE(UnimplementedCommand.class),
    FORCE_ADD(UnimplementedCommand.class),
    FORCE_DELETE(UnimplementedCommand.class),
    FORCE_PARENT(UnimplementedCommand.class),
    FORCE_ADD_ENTITY(UnimplementedCommand.class),
    FORCE_ASSIGN_FULL(UnimplementedCommand.class),
    ENTITY_VISIBILITY_INDICATOR(UnimplementedCommand.class),
    ADD_SMOKE_CLOUD(UnimplementedCommand.class),
    CHANGE_HEX(UnimplementedCommand.class),
    CHANGE_HEXES(UnimplementedCommand.class),
    BLDG_ADD(UnimplementedCommand.class),
    BLDG_REMOVE(UnimplementedCommand.class),
    BLDG_UPDATE(UnimplementedCommand.class),
    BLDG_COLLAPSE(UnimplementedCommand.class),
    BLDG_EXPLODE(UnimplementedCommand.class),
    PHASE_CHANGE(UnimplementedCommand.class),
    TURN(UnimplementedCommand.class),
    ROUND_UPDATE(UnimplementedCommand.class),
    SENDING_BOARD(UnimplementedCommand.class),
    SENDING_ILLUM_HEXES(UnimplementedCommand.class),
    CLEAR_ILLUM_HEXES(UnimplementedCommand.class),
    SENDING_ENTITIES(UnimplementedCommand.class),
    SENDING_PLAYERS(UnimplementedCommand.class),
    SENDING_TURNS(UnimplementedCommand.class),
    SENDING_REPORTS(UnimplementedCommand.class),
    SENDING_REPORTS_SPECIAL(UnimplementedCommand.class),
    SENDING_REPORTS_TACTICAL_GENIUS(UnimplementedCommand.class),
    SENDING_REPORTS_ALL(UnimplementedCommand.class),
    SENDING_GAME_SETTINGS(UnimplementedCommand.class),
    SENDING_MAP_DIMENSIONS(UnimplementedCommand.class),
    SENDING_MAP_SETTINGS(UnimplementedCommand.class),
    END_OF_GAME(UnimplementedCommand.class),
    DEPLOY_MINEFIELDS(UnimplementedCommand.class),
    REVEAL_MINEFIELD(UnimplementedCommand.class),
    REMOVE_MINEFIELD(UnimplementedCommand.class),
    SENDING_MINEFIELDS(UnimplementedCommand.class),
    UPDATE_MINEFIELDS(UnimplementedCommand.class),
    REROLL_INITIATIVE(UnimplementedCommand.class),
    UNLOAD_STRANDED(UnimplementedCommand.class),
    SET_ARTILLERY_AUTOHIT_HEXES(UnimplementedCommand.class),
    SENDING_ARTILLERY_ATTACKS(UnimplementedCommand.class),
    SENDING_FLARES(UnimplementedCommand.class),
    SERVER_CORRECT_NAME(UnimplementedCommand.class),
    SEND_SAVEGAME(UnimplementedCommand.class),
    LOAD_SAVEGAME(UnimplementedCommand.class),
    LOAD_GAME(LoadGameCommand.class),
    SENDING_SPECIAL_HEX_DISPLAY(UnimplementedCommand.class),
    SPECIAL_HEX_DISPLAY_APPEND(UnimplementedCommand.class),
    SPECIAL_HEX_DISPLAY_DELETE(UnimplementedCommand.class),
    CUSTOM_INITIATIVE(UnimplementedCommand.class),
    FORWARD_INITIATIVE(UnimplementedCommand.class),
    SENDING_PLANETARY_CONDITIONS(UnimplementedCommand.class),
    SQUADRON_ADD(UnimplementedCommand.class),
    ENTITY_CALLEDSHOTCHANGE(UnimplementedCommand.class),
    ENTITY_MOUNTED_FACING_CHANGE(UnimplementedCommand.class),
    SENDING_AVAILABLE_MAP_SIZES(UnimplementedCommand.class),
    ENTITY_LOAD(UnimplementedCommand.class),
    ENTITY_NOVA_NETWORK_CHANGE(UnimplementedCommand.class),
    RESET_ROUND_DEPLOYMENT(UnimplementedCommand.class),
    SENDING_TAG_INFO(UnimplementedCommand.class),
    RESET_TAG_INFO(UnimplementedCommand.class),
    CLIENT_FEEDBACK_REQUEST(UnimplementedCommand.class),
    CFR_DOMINO_EFFECT(UnimplementedCommand.class),
    CFR_AMS_ASSIGN(UnimplementedCommand.class),
    CFR_APDS_ASSIGN(UnimplementedCommand.class),
    CFR_HIDDEN_PBS(UnimplementedCommand.class),
    CFR_TELEGUIDED_TARGET(UnimplementedCommand.class),
    CFR_TAG_TARGET(UnimplementedCommand.class),
    GAME_VICTORY_EVENT(UnimplementedCommand.class);
    //endregion Enum Declarations

    private final Class<? extends AbstractPacketCommand> commandClass;
    private AbstractPacketCommand command;

    PacketCommand(Class<? extends AbstractPacketCommand> commandClass) {
        this.commandClass = commandClass;
    }

    public AbstractPacketCommand getCommand(GameManager manager) {
        try {
            if (command == null) {
                command = commandClass.getConstructor(GameManager.class).newInstance(manager);
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
        return command;
    }

    //region Boolean Comparison Methods
    public boolean isSendingBoard() {
        return this == SENDING_BOARD;
    }

    public boolean isSendingReportsTacticalGenius() {
        return this == SENDING_REPORTS_TACTICAL_GENIUS;
    }

    public boolean isCFRDominoEffect() {
        return this == CFR_DOMINO_EFFECT;
    }

    public boolean isCFRAMSAssign() {
        return this == CFR_AMS_ASSIGN;
    }

    public boolean isCFRAPDSAssign() {
        return this == CFR_APDS_ASSIGN;
    }

    public boolean isCFRHiddenPBS() {
        return this == CFR_HIDDEN_PBS;
    }

    public boolean isCFRTeleguidedTarget() {
        return this == CFR_TELEGUIDED_TARGET;
    }

    public boolean isCFRTagTarget() {
        return this == CFR_TAG_TARGET;
    }

    public boolean isGameVictoryEvent() {
        return this == GAME_VICTORY_EVENT;
    }

    public boolean isCFR() {
        return isCFRDominoEffect() || isCFRAMSAssign() || isCFRAPDSAssign() || isCFRHiddenPBS()
                || isCFRTeleguidedTarget() || isCFRTagTarget();
    }
    //endregion Boolean Comparison Methods
}
