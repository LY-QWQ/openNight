package client.opennight.event;

public interface Cancellable {
    boolean isCancelled();

    void setCancelled(boolean var1);
}