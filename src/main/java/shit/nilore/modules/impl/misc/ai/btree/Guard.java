package shit.nilore.modules.impl.misc.ai.btree;

import shit.nilore.modules.impl.misc.ai.Blackboard;

import java.util.function.Predicate;

public class Guard extends BTNode {

    private final Predicate<Blackboard> predicate;
    private final BTNode child;

    public Guard(Predicate<Blackboard> predicate, BTNode child) {
        this.predicate = predicate;
        this.child = child;
    }

    @Override
    public Status tick(Blackboard bb) {
        if (!predicate.test(bb)) {
            return status = Status.FAILURE;
        }
        return status = child.tick(bb);
    }

    @Override
    public void reset() {
        super.reset();
        child.reset();
    }
}
