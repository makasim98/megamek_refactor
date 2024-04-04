package megamek.server.phases;

import jakarta.mail.Message;
import megamek.common.*;
import megamek.common.actions.*;
import megamek.common.enums.GamePhase;
import megamek.common.force.Forces;
import megamek.common.options.GameOptions;
import megamek.common.util.EmailService;
import megamek.server.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PhasesTest {

    private GameManager manager;
    private Game game;
    private Board board;
    private Vector<EntityAction> entityActions;
    private Entity tank;

    protected Game createMockedGame() {
        Game testGame = mock(Game.class);
        Forces testForces = new Forces(testGame);
        when(testGame.getGameListeners()).thenReturn(new Vector<>());
        when(testGame.getEntities()).thenReturn(Collections.emptyIterator());
        when(testGame.getEntitiesVector()).thenReturn(Collections.emptyList());
        when(testGame.getPlayers()).thenReturn(Collections.emptyEnumeration());
        when(testGame.getPlayersList()).thenReturn(Collections.emptyList());
        when(testGame.getAttacks()).thenReturn(Collections.emptyEnumeration());
        when(testGame.getAttacksVector()).thenReturn(new Vector<>());
        when(testGame.getForces()).thenReturn(testForces);
        when(testGame.getOptions()).thenReturn(new GameOptions());
        when(testGame.getMapSettings()).thenCallRealMethod();
        when(testGame.getPlanetaryConditions()).thenReturn(Mockito.mock(PlanetaryConditions.class));
        when(testGame.getPhase()).thenReturn(GamePhase.MOVEMENT);
        when(testGame.getBoard()).thenReturn(board);
        when(testGame.getActions()).thenReturn(entityActions.elements());
        when(testGame.getEntity(any(int.class))).thenReturn(tank);

        AttackAction attackAction = Mockito.mock(DfaAttackAction.class);
        Vector<AttackAction> attackActions = new Vector<>();
        attackActions.add(attackAction);
        when(testGame.getCharges()).thenReturn(attackActions.elements());
        when(testGame.getRams()).thenReturn(attackActions.elements());
        when(testGame.getTeleMissileAttacks()).thenReturn(attackActions.elements());


        Vector<Entity> oogEntities = new Vector<>();
        oogEntities.add(tank);
        when(testGame.getOutOfGameEntitiesVector()).thenReturn(oogEntities);
        when(testGame.getSelectedEntities(any())).thenReturn(oogEntities.iterator());
        when(testGame.getRetreatedEntities()).thenReturn(oogEntities.elements());
        when(testGame.getGraveyardEntities()).thenReturn(oogEntities.elements());
        when(testGame.getDevastatedEntities()).thenReturn(oogEntities.elements());
        return testGame;
    }

    private Board createMockedBoard() {
        Board mockedBoard = Mockito.mock(Board.class);
        when(mockedBoard.inAtmosphere()).thenReturn(true);
        when(mockedBoard.inSpace()).thenReturn(true);
        when(mockedBoard.getHex(any())).thenReturn(new Hex());
        when(mockedBoard.getBuildings()).thenReturn(Collections.emptyEnumeration());
        return mockedBoard;
    }

    private Vector<EntityAction> createActionVector() {
        Vector<EntityAction> actions = new Vector<>();
        actions.add(new FlipArmsAction(50, false));
        actions.add(new TorsoTwistAction(50, 4));
        actions.add(new FindClubAction(50));
        actions.add(new UnjamAction(50));
        actions.add(new ClearMinefieldAction(50, Minefield.createMinefield(new Coords(4,5), 4, 5, 6)));
        actions.add(new TriggerAPPodAction(50, 40));
        actions.add(new TriggerBPodAction(50, 50, 60));
        actions.add(new SearchlightAttackAction(50, 70));
        actions.add(new UnjamTurretAction(50));
        actions.add(new RepairWeaponMalfunctionAction(50, 60));
        actions.add(new DisengageAction(50));
        actions.add(new ActivateBloodStalkerAction(50, 80));
        return actions;
    }

    private Entity createEntity() {
        Tank tmp = Mockito.mock(Tank.class);
        when(tmp.isDestroyed()).thenReturn(false);
        when(tmp.isDeployed()).thenReturn(true);
        when(tmp.isDoomed()).thenReturn(true);
        when(tmp.getPosition()).thenReturn(new Coords(4, 9));
        when(tmp.canChangeSecondaryFacing()).thenReturn(true);
        Crew crew = Mockito.mock(Crew.class);
        when(crew.rollPilotingSkill()).thenReturn(Mockito.mock(Roll.class));
        when(tmp.getCrew()).thenReturn(crew);
        when(tmp.getId()).thenReturn(50);
        when(tmp.getEquipment(any(int.class))).thenReturn(new Mounted(tmp, new EquipmentType()));
        when(tmp.getJammedWeapons()).thenReturn(new ArrayList<>());

        PilotingRollData data = Mockito.mock(PilotingRollData.class);
        when(data.getValueAsString()).thenReturn("Roll Data");
        when(data.getDesc()).thenReturn("Description");
        when(tmp.getBasePilotingRoll()).thenReturn(data);

        return tmp;
    }

    @BeforeAll
    public void init() {
        entityActions = createActionVector();
        tank = createEntity();
        board = createMockedBoard();
        game = createMockedGame();
        manager = Mockito.mock(GameManager.class);
        when(manager.getGame()).thenReturn(game);
        when(manager.getvPhaseReport()).thenReturn(new Vector<>());
        when(manager.getPhysicalResults()).thenReturn(new Vector<>());

        Vector<DynamicTerrainProcessor> processors = new Vector<>();
        processors.add(new FireProcessor(manager));
        processors.add(new GeyserProcessor(manager));
        when(manager.getTerrainProcessors()).thenReturn(processors);

        Player player = Mockito.mock(Player.class);
        Vector<Player> players = new Vector<>();
        players.add(player);
        EmailService email = Mockito.mock(EmailService.class);
        when(email.getEmailablePlayers(any())).thenReturn(players);
        Server server = Mockito.mock(Server.class);
        when(server.getEmailService()).thenReturn(email);
        MockedStatic<Server> staticMock = Mockito.mockStatic(Server.class);
        staticMock.when(Server::getServerInstance).thenReturn(server);

    }

    @Test
    public void testPhases() {
        for(GamePhase x:GamePhase.values()) {
            AbstractGamePhase phase = x.getPhase(manager);
            phase.preparePhase();
            phase.executePhase();
            phase.endPhase();
        }
    }
}
