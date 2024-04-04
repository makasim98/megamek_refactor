package megamek.server.phases;

import megamek.common.*;
import megamek.common.enums.GamePhase;
import megamek.common.options.OptionsConstants;
import megamek.common.util.BoardUtilities;
import megamek.common.util.fileUtils.MegaMekFile;
import megamek.server.GameManager;

import java.util.*;

public class ExchangePhase extends AbstractGamePhase{
    public ExchangePhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        game.addReports(gameManager.getvPhaseReport());
        return Optional.of(GamePhase.SET_ARTILLERY_AUTOHIT_HEXES);
    }

    @Override
    public void executePhase() {
        super.executePhase();
        gameManager.resetPlayersDone();
        // Update initial BVs, as things may have been modified in lounge
        for (Entity e : game.getEntitiesVector()) {
            e.setInitialBV(e.calculateBattleValue(false, false));
        }
        gameManager.calculatePlayerInitialCounts();
        // Build teams vector
        game.setupTeams();
        applyBoardSettings();
        game.getPlanetaryConditions().determineWind();
        gameManager.send(gameManager.createPlanetaryConditionsPacket());
        // transmit the board to everybody
        gameManager.send(gameManager.createBoardPacket());
        game.setupRoundDeployment();
        game.setVictoryContext(new HashMap<>());
        game.createVictoryConditions();
        // some entities may need to be checked and updated
        checkEntityExchange();
    }

    /**
     * Applies board settings. This loads and combines all the boards that were
     * specified into one mega-board and sets that board as current.
     */
    private void applyBoardSettings() {
        MapSettings mapSettings = game.getMapSettings();
        mapSettings.chooseSurpriseBoards();
        Board[] sheetBoards = new Board[mapSettings.getMapWidth() * mapSettings.getMapHeight()];
        List<Boolean> rotateBoard = new ArrayList<>();
        for (int i = 0; i < (mapSettings.getMapWidth() * mapSettings.getMapHeight()); i++) {
            sheetBoards[i] = new Board();
            String name = mapSettings.getBoardsSelectedVector().get(i);
            boolean isRotated = false;
            if (name.startsWith(Board.BOARD_REQUEST_ROTATION)) {
                // only rotate boards with an even width
                if ((mapSettings.getBoardWidth() % 2) == 0) {
                    isRotated = true;
                }
                name = name.substring(Board.BOARD_REQUEST_ROTATION.length());
            }
            if (name.startsWith(MapSettings.BOARD_GENERATED)
                    || (mapSettings.getMedium() == MapSettings.MEDIUM_SPACE)) {
                sheetBoards[i] = BoardUtilities.generateRandom(mapSettings);
            } else {
                sheetBoards[i].load(new MegaMekFile(Configuration.boardsDir(), name + ".board").getFile());
                BoardUtilities.flip(sheetBoards[i], isRotated, isRotated);
            }
            rotateBoard.add(isRotated);
        }
        Board newBoard = BoardUtilities.combine(mapSettings.getBoardWidth(),
                mapSettings.getBoardHeight(), mapSettings.getMapWidth(),
                mapSettings.getMapHeight(), sheetBoards, rotateBoard,
                mapSettings.getMedium());
        if (game.getOptions().getOption(OptionsConstants.BASE_BRIDGECF).intValue() > 0) {
            newBoard.setBridgeCF(game.getOptions().getOption(OptionsConstants.BASE_BRIDGECF).intValue());
        }
        if (!game.getOptions().booleanOption(OptionsConstants.BASE_RANDOM_BASEMENTS)) {
            newBoard.setRandomBasementsOff();
        }
        if (game.getPlanetaryConditions().isTerrainAffected()) {
            BoardUtilities.addWeatherConditions(newBoard, game.getPlanetaryConditions().getWeather(),
                    game.getPlanetaryConditions().getWindStrength());
        }
        game.setBoard(newBoard);
    }

    /**
     * loop through entities in the exchange phase (i.e. after leaving
     * chat lounge) and do any actions that need to be done
     */
    private void checkEntityExchange() {
        for (Iterator<Entity> entities = game.getEntities(); entities.hasNext(); ) {
            Entity entity = entities.next();
            // apply bombs
            if (entity.isBomber()) {
                ((IBomber) entity).applyBombs();
            }

            if (entity.isAero()) {
                IAero a = (IAero) entity;
                if (a.isSpaceborne()) {
                    // altitude and elevation don't matter in space
                    a.liftOff(0);
                } else {
                    // check for grounding
                    if (game.getBoard().inAtmosphere() && !entity.isAirborne()) {
                        // you have to be airborne on the atmospheric map
                        a.liftOff(entity.getAltitude());
                    }
                }

                if (entity.isFighter()) {
                    a.updateWeaponGroups();
                    entity.loadAllWeapons();
                }
            }

            // if units were loaded in the chat lounge, I need to keep track of
            // it here because they can get dumped in the deployment phase
            if (!entity.getLoadedUnits().isEmpty()) {
                Vector<Integer> v = new Vector<>();
                for (Entity en : entity.getLoadedUnits()) {
                    v.add(en.getId());
                }
                entity.setLoadedKeepers(v);
            }

            if (game.getOptions().booleanOption(OptionsConstants.ADVAERORULES_AERO_SANITY)
                    && (entity.isAero())) {
                Aero a = null;
                if (entity instanceof Aero) {
                    a = (Aero) entity;
                }
                if (entity.isCapitalScale()) {
                    if (a != null) {
                        int currentSI = a.getSI() * 20;
                        a.initializeSI(a.get0SI() * 20);
                        a.setSI(currentSI);
                    }
                    if (entity.isCapitalFighter()) {
                        ((IAero) entity).autoSetCapArmor();
                        ((IAero) entity).autoSetFatalThresh();
                    } else {
                        // all armor and SI is going to be at standard scale, so
                        // we need to adjust
                        for (int loc = 0; loc < entity.locations(); loc++) {
                            if (entity.getArmor(loc) > 0) {
                                int currentArmor = entity.getArmor(loc) * 10;
                                entity.initializeArmor(entity.getOArmor(loc) * 10, loc);
                                entity.setArmor(currentArmor, loc);

                            }
                        }
                    }
                } else if (a != null) {
                    int currentSI = a.getSI() * 2;
                    a.initializeSI(a.get0SI() * 2);
                    a.setSI(currentSI);
                }
            }
            if (entity.getsAutoExternalSearchlight()) {
                entity.setExternalSearchlight(true);
            }
            gameManager.entityUpdate(entity.getId());

            // Remove hot-loading some from LRMs for meks
            if (!game.getOptions().booleanOption(OptionsConstants.ADVCOMBAT_HOTLOAD_IN_GAME)) {
                for (Entity e : game.getEntitiesVector()) {
                    // Vehicles are allowed to hot load, just meks cannot
                    if (!(e instanceof Mech)) {
                        continue;
                    }
                    for (Mounted weapon : e.getWeaponList()) {
                        weapon.getType().removeMode("HotLoad");
                    }
                    for (Mounted ammo : e.getAmmo()) {
                        ammo.getType().removeMode("HotLoad");
                    }
                }
            }
        }
    }
}
