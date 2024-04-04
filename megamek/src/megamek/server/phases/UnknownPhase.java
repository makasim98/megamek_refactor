package megamek.server.phases;

import megamek.common.enums.GamePhase;
import megamek.server.GameManager;

import java.util.Optional;

public class UnknownPhase extends AbstractGamePhase{
    public UnknownPhase(GameManager manager) {
        super(manager);
    }

    @Override
    public Optional<GamePhase> endPhase() {
        return Optional.empty();
    }
}
