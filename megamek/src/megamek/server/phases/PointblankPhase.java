package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class PointblankPhase extends AbstractGamePhase{
    public PointblankPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.empty();
    }
}
