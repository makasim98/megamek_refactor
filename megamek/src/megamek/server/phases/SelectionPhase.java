package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class SelectionPhase extends AbstractGamePhase{
    public SelectionPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.empty();
    }
}
