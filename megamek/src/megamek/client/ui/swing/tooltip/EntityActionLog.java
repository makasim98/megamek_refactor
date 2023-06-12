package megamek.client.ui.swing.tooltip;

import megamek.client.Client;
import megamek.client.ui.Messages;
import megamek.common.*;
import megamek.common.actions.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * An ordered List-like collection of EntityActions with a cached description of each action,
 * created as the action is added.
 */
public class EntityActionLog implements Collection<EntityAction> {
    final Game game;
    final Client client;
    final protected ArrayList<EntityAction> actions = new ArrayList<EntityAction>();
//    final protected ArrayList<WeaponAttackAction> weaponAttackActions = new ArrayList<WeaponAttackAction>();
    // cache to prevent regeneration of info if action already processed
    final HashMap<EntityAction, String> infoCache = new HashMap<EntityAction, String>();
    final ArrayList<String> descriptions = new ArrayList<>();

    public EntityActionLog(Client client) {
        this.client = client;
        this.game = client.getGame();
    }

    /** @return a list of description strings. Note that there may be fewer than the number of actions
     * as similar actions are summarized in a single entry
     */
    public List<String> getDescriptions() {
        return descriptions;
    }

    public void rebuildDescriptions() {
        descriptions.clear();
        for (EntityAction entityAction : actions) {
            addDescription(entityAction);
        }
    }

    /**
     * @return a clone of the internal List of EntityActions
     */
    public Vector<EntityAction> toVector() {
        return new Vector<>(actions);
    }

    /**
     * remove all items from all internal collections
     */
    @Override
    public void clear() {
        actions.clear();
        infoCache.clear();
        descriptions.clear();
    }

    @Override
    public boolean add(EntityAction entityAction) {
        if (!actions.add(entityAction)) {
            return false;
        }
        rebuildDescriptions();
        return true;
    }

    public void add(int index, EntityAction entityAction) {
        actions.add(index, entityAction);
        rebuildDescriptions();
    }

    /**
     * Remove an item and its description cache
     * @param entityAction
     */
    @Override
    public boolean remove(Object o) {
        if (!actions.remove(o)) {
            return false;
        }
        infoCache.remove(o);
        rebuildDescriptions();
        return true;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return actions.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends EntityAction> c) {
        if (!actions.addAll(c)) {
            return false;
        }
        rebuildDescriptions();
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        if (!actions.removeAll(c)) {
            return false;
        }
        for (var a : c) {
            infoCache.remove(a);
        }
        rebuildDescriptions();
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return actions.retainAll(c);
    }

    public EntityAction firstElement() {
        return actions.isEmpty() ? null : actions.get(0);
    }

    public EntityAction lastElement() {
        return actions.isEmpty() ? null : actions.get(actions.size()-1);
    }

    @Override
    public boolean isEmpty() {
        return actions.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return actions.contains(o);
    }

    void addDescription(EntityAction entityAction) {
        if (entityAction instanceof WeaponAttackAction) {
            // ToHit may change as other actions are added,
            // so always re-evaluate all WeaponAttack Actions
            addEntityAction((WeaponAttackAction) entityAction);
        } else if (infoCache.containsKey(entityAction)) {
            // reuse previous description
            descriptions.add(infoCache.get(entityAction));
        } else if (entityAction instanceof KickAttackAction) {
            addEntityAction((KickAttackAction) entityAction);
        } else if (entityAction instanceof PunchAttackAction) {
            addEntityAction((PunchAttackAction) entityAction);
        } else if (entityAction instanceof PushAttackAction) {
            addEntityAction((PushAttackAction) entityAction);
        } else if (entityAction instanceof ClubAttackAction) {
            addEntityAction((ClubAttackAction) entityAction);
        } else if (entityAction instanceof ChargeAttackAction) {
            addEntityAction((ChargeAttackAction) entityAction);
        } else if (entityAction instanceof DfaAttackAction) {
            addEntityAction((DfaAttackAction) entityAction);
        } else if (entityAction instanceof ProtomechPhysicalAttackAction) {
            addEntityAction((ProtomechPhysicalAttackAction) entityAction);
        } else if (entityAction instanceof SearchlightAttackAction) {
            addEntityAction((SearchlightAttackAction) entityAction);
        } else if (entityAction instanceof SpotAction) {
            addEntityAction((SpotAction) entityAction);
        } else {
            addEntityAction(entityAction);
        }
    }

    /**
     * Adds a weapon to this attack
     */
    void addEntityAction(WeaponAttackAction attack) {
        ToHitData toHit = attack.toHit(game, true);
        String table = toHit.getTableDesc();
        final String buffer = toHit.getValueAsString() + ((!table.isEmpty()) ? ' '+table : "");
        final Entity entity = game.getEntity(attack.getEntityId());
        final String weaponName =  ((WeaponType) entity.getEquipment(attack.getWeaponId()).getType()).getName();

        //add to an existing entry if possible
        boolean found = false;
        ListIterator<String> i = descriptions.listIterator();
        while (i.hasNext()) {
            String s = i.next();
            if (s.startsWith(weaponName)) {
                i.set(s + ", " + buffer);
                found = true;
                break;
            }
        }

        if (!found) {
            descriptions.add(weaponName + Messages.getString("BoardView1.needs") + buffer);
        }
    }

    void addEntityAction(KickAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            String rollLeft;
            String rollRight;
            final int leg = attack.getLeg();
            switch (leg) {
                case KickAttackAction.BOTH:
                    rollLeft = KickAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                    rollRight = KickAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                    buffer = Messages.getString("BoardView1.kickBoth", rollLeft, rollRight);
                    break;
                case KickAttackAction.LEFT:
                    rollLeft = KickAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.LEFT).getValueAsString();
                    buffer = Messages.getString("BoardView1.kickLeft", rollLeft);
                    break;
                case KickAttackAction.RIGHT:
                    rollRight = KickAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), KickAttackAction.RIGHT).getValueAsString();
                    buffer = Messages.getString("BoardView1.kickRight", rollRight);
                    break;
                default:
                    buffer = "Error on kick action";
            }
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(PunchAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            String rollLeft;
            String rollRight;
            final int arm = attack.getArm();
            switch (arm) {
                case PunchAttackAction.BOTH:
                    rollLeft = PunchAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT, false).getValueAsString();
                    rollRight = PunchAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT, false).getValueAsString();
                    buffer = Messages.getString("BoardView1.punchBoth", rollLeft, rollRight);
                    break;
                case PunchAttackAction.LEFT:
                    rollLeft = PunchAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.LEFT, false).getValueAsString();
                    buffer = Messages.getString("BoardView1.punchLeft", rollLeft);
                    break;
                case PunchAttackAction.RIGHT:
                    rollRight = PunchAttackAction.toHit(game, attack.getEntityId(), game.getTarget(attack.getTargetType(), attack.getTargetId()), PunchAttackAction.RIGHT, false).getValueAsString();
                    buffer = Messages.getString("BoardView1.punchRight", rollRight);
                    break;
                default:
                    buffer = "Error on punch action";
            }
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(PushAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            final String roll = attack.toHit(game).getValueAsString();
            buffer = Messages.getString("BoardView1.PushAttackAction", roll);
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(ClubAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            final String roll = attack.toHit(game).getValueAsString();
            final String club = attack.getClub().getName();
            buffer = Messages.getString("BoardView1.ClubAttackAction", club, roll);
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(ChargeAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            final String roll = attack.toHit(game).getValueAsString();
            buffer = Messages.getString("BoardView1.ChargeAttackAction", roll);
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(DfaAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            final String roll = attack.toHit(game).getValueAsString();
            buffer = Messages.getString("BoardView1.DfaAttackAction", roll);
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(ProtomechPhysicalAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            final String roll = attack.toHit(game).getValueAsString();
            buffer = Messages.getString("BoardView1.ProtomechPhysicalAttackAction", roll);
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(SearchlightAttackAction attack) {
        String buffer;
        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            Entity target = game.getEntity(attack.getTargetId());
            buffer = Messages.getString("BoardView1.SearchlightAttackAction")  + ((target != null) ? ' ' +target.getShortName() : "");
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(SpotAction attack) {
        String buffer;

        if (infoCache.containsKey(attack)) {
            buffer = infoCache.get(attack);
        } else {
            Entity target = game.getEntity(attack.getTargetId());
            buffer = Messages.getString("BoardView1.SpotAction", (target != null) ? target.getShortName() : "" );
            infoCache.put(attack, buffer);
        }
        descriptions.add(buffer);
    }

    void addEntityAction(EntityAction entityAction) {
        String buffer;

        if (infoCache.containsKey(entityAction)) {
            buffer = infoCache.get(entityAction);
        } else {
            String typeName = entityAction.getClass().getTypeName();
            buffer = typeName.substring(typeName.lastIndexOf('.') + 1);
            infoCache.put(entityAction, buffer);
        }
        descriptions.add(buffer);
    }

    @Override
    public int size() {
        return actions.size();
    }

    @Override
    public Iterator<EntityAction> iterator() {
        return actions.iterator();
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return actions.toArray(a);
    }

    @Override
    public Stream<EntityAction> stream() {
        return actions.stream();
    }
}
