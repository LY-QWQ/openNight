package client.nilore.utils.misc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.JOptionPane;
import client.nilore.manager.ConfigManager;
import client.nilore.utils.math.MathUtil;

public final class SoundUtil {
    public static void playSound(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, 0);
    }

    public static void playSound(String fileName, float gain) {
        File file = new File(ConfigManager.CONFIG_DIR, fileName);
        if (!file.exists()) {
            System.out.println("SoundUtil: file not found - " + file.getAbsolutePath());
            return;
        }
        new Thread(() -> {
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                 AudioInputStream audioIn = AudioSystem.getAudioInputStream(bis)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                try {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(gain);
                } catch (IllegalArgumentException ignored) {}
                clip.start();
            } catch (Exception ex) {
                System.out.println("SoundUtil: failed to play " + fileName + " - " + ex.getMessage());
            }
        }, "SoundPlayer-" + MathUtil.randomInt(0, 100)).start();
    }
}