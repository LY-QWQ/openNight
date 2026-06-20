package shit.nilore.modules.impl.misc.ai.btree;

import shit.nilore.modules.impl.misc.ai.Blackboard;

public class Repeater extends BTNode {

    private final BTNode child;
    private final int maxRepeats;
    private int count;

    public Repeater(BTNode child, int maxRepeats) {
        this.child = child;
        this.maxRepeats = maxRepeats;
    }

    @Override
    public Status tick(Blackboard bb) {
        for (int i = count; i < maxRepeats; i++) {
            Status s = child.tick(bb);
            if (s == Status.RUNNING) {
                count = i;
                return status = Status.RUNNING;
            }
            if (s == Status.FAILURE) {
                count = 0;
                return status = Status.FAILURE;
            }
        }
        count = 0;
        return status = Status.SUCCESS;
    }

    @Override
    public void reset() {
        super.reset();
        count = 0;
        child.reset();
    }
}
