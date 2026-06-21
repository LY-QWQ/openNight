package shit.nilore.modules.impl.misc;

import shit.nilore.gui.MusicPlayerScreen;
import shit.nilore.modules.Category;
import shit.nilore.modules.Module;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.settings.impl.NumberSetting;

public class MusicPlayer extends Module {
    public static final AudioPlayer AUDIO_PLAYER = new AudioPlayer();

    private final NumberSetting volume = new NumberSetting("Volume", 80, 0, 100, 1);

    public MusicPlayer() {
        super("MusicPlayer", Category.MISC);
    }

    @Override
    protected void onEnable() {
        try {
            AUDIO_PLAYER.setVolume(volume.getValue().floatValue() / 100.0f);
            mc.setScreen(new MusicPlayerScreen());
        } catch (Exception e) {
            logger.error("Error opening MusicPlayer", e);
        } finally {
            this.setEnabled(false);
        }
    }
}
