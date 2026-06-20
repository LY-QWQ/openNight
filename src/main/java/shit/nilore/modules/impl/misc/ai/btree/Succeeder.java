package shit.nilore.modules.impl.misc.ai.btree;

import shit.nilore.modules.impl.misc.ai.Blackboard;

public class Succeeder extends BTNode {

    private final BTNode child;

    public Succeeder(BTNode child) {
        this.child = child;
    }

    @Override
    public Status tick(Blackboard bb) {
        child.tick(bb);
        return status = Status.SUCCESS;
    }

    @Override
    public void reset() {
        super.reset();
        child.reset();
    }
}
