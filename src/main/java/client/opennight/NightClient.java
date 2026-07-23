package client.opennight;

import asm.patchify.loader.ClassAgent;
import asm.patchify.loader.PatchRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import client.opennight.event.EventBus;
import client.opennight.event.EventTarget;
import client.opennight.event.impl.TickEvent;
import client.opennight.gui.IntroAnimation;
import client.opennight.manager.CommandManager;
import client.opennight.manager.ConfigManager;
import client.opennight.manager.HudManager;
import client.opennight.manager.LagManager;
import client.opennight.manager.ModuleManager;
import client.opennight.manager.TargetManager;
import client.opennight.patch.ChatScreenPatch;
import client.opennight.patch.ClientLevelPatch;
import client.opennight.patch.ConnectionPatch;
import client.opennight.patch.EntityPatch;
import client.opennight.patch.EntityRenderDispatcherPatch;
import client.opennight.patch.EntityRendererPatch;
import client.opennight.patch.FriendlyByteBufPatch;
import client.opennight.patch.GuiPatch;
import client.opennight.patch.BlockOcclusionCachePatch;
import client.opennight.patch.GameRendererPatch;
import client.opennight.patch.HumanoidModelPatch;
import client.opennight.patch.ItemInHandLayerPatch;
import client.opennight.patch.ItemInHandRendererPatch;
import client.opennight.patch.ItemPatch;
import client.opennight.patch.KeyboardHandlerPatch;
import client.opennight.patch.KeyboardInputPatch;
import client.opennight.patch.LevelRendererPatch;
import client.opennight.patch.LivingEntityPatch;
import client.opennight.patch.LivingEntityRendererPatch;
import client.opennight.patch.LocalPlayerPatch;
import client.opennight.patch.MinecraftPatch;
import client.opennight.patch.PacketUtilsPatch;
import client.opennight.patch.PlayerPatch;
import client.opennight.patch.PlayerTabOverlayPatch;
import client.opennight.asm.Bootstrap;
import client.opennight.utils.rotation.RotationHandler;
import client.opennight.utils.misc.Assets;

@Mod(value = "night")
@Getter
@Setter
public class NightClient extends ClientBase {
    @Getter
    public static NightClient instance;
    public static final String CLIENT_NAME = "OpenNIGHT";
    public static final String CLIENT_NAME_UPPER = "OPENNIGHT";
    public static final String VERSION = "1.0";
    public static float serverTickRate;
    public static boolean isReady;
    public static boolean isMCPMapped;
    public static String configDir = System.getProperty("user.home") + File.separator + ".opennight";
    public static String username = "";

    private static final String[] CLOUD_ASSET_NAMES = { "panel.png", "ptr.png", "lie.wav", "truth.wav" };

    private EventBus eventBus;
    private RotationHandler rotationHandler;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private LagManager lagManager;
    private TargetManager targetManager;
    private int reconnectAttempts;

    public NightClient() {
        if (instance == null) {
            instance = this;
            this.init();
        }
    }

    private void init() {
        try {
            username = System.getProperty("user.name", "Player");
            File dir = new File(configDir);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Failed to create config directory at {}", configDir);
            }
            mc = getMcInstance();
            this.eventBus = new EventBus();
            this.rotationHandler = new RotationHandler();
            this.eventBus.register(this.rotationHandler);
            this.moduleManager = new ModuleManager();
            this.hudManager = new HudManager();
            this.commandManager = new CommandManager();
            this.configManager = new ConfigManager();
            this.extractCloudAssets();
            this.lagManager = new LagManager();
            this.targetManager = new TargetManager();
            this.eventBus.register(this.hudManager);
            this.eventBus.register(this.lagManager);
            this.eventBus.register(this.targetManager);
            this.eventBus.register(this);
            this.commandManager.initCommands();
            this.eventBus.register(new IntroAnimation());
            Bootstrap.init();
            registerPatches();
            if (ClassAgent.getInstrumentation() != null) {
                ClassAgent.installPatchesAndRetransform();
            } else {
                logger.warn("ClassAgent not attached. Launch with `./gradlew runClient0` so the agent jvmArg is set.");
            }
            isReady = true;
            logger.info("{} v{} initialized.", CLIENT_NAME, VERSION);
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
        }
    }

    private boolean moduleInit = false;

    @EventTarget
    public void onTick(TickEvent e) {
        if (isReady() && !moduleInit) {
            moduleInit = true;
            this.moduleManager.initModules();
            this.configManager.loadAll();
        }
    }

    public static boolean isReady() {
        return instance != null
                && NightClient.instance.eventBus != null
                && isReady
                && mc != null
                && mc.player != null
                && !username.isEmpty()
                && mc.player.tickCount > 5;
    }

    public void shutdown() {
        isReady = false;
        if (this.configManager != null) {
            this.configManager.saveAll();
        }
    }

    private void extractCloudAssets() {
        File targetDir = ConfigManager.CONFIG_DIR;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            logger.warn("Failed to create config directory at {}", targetDir);
            return;
        }
        for (String name : CLOUD_ASSET_NAMES) {
            File outFile = new File(targetDir, name);
            if (outFile.exists()) continue;
            try (InputStream in = Assets.open("/assets/opennight/cloud_assets/" + name)) {
                if (in == null) {
                    logger.warn("Cloud asset missing on classpath: {}", name);
                    continue;
                }
                try (OutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            } catch (IOException ioException) {
                logger.error("Failed to extract cloud asset {}", name, ioException);
            }
        }
    }

    public static void registerPatches() {
        PatchRegistry.register(MinecraftPatch.class);
        PatchRegistry.register(LocalPlayerPatch.class);
        PatchRegistry.register(LivingEntityPatch.class);
        PatchRegistry.register(EntityPatch.class);
        PatchRegistry.register(PlayerPatch.class);
        PatchRegistry.register(ClientLevelPatch.class);
        PatchRegistry.register(ConnectionPatch.class);
        PatchRegistry.register(PacketUtilsPatch.class);
        PatchRegistry.register(KeyboardHandlerPatch.class);
        PatchRegistry.register(KeyboardInputPatch.class);
        PatchRegistry.register(ChatScreenPatch.class);
        PatchRegistry.register(EntityRendererPatch.class);
        PatchRegistry.register(EntityRenderDispatcherPatch.class);
        PatchRegistry.register(LevelRendererPatch.class);
        PatchRegistry.register(GameRendererPatch.class);
        PatchRegistry.register(ItemInHandRendererPatch.class);
        PatchRegistry.register(ItemInHandLayerPatch.class);
        PatchRegistry.register(HumanoidModelPatch.class);
        PatchRegistry.register(LivingEntityRendererPatch.class);
        PatchRegistry.register(ItemPatch.class);
        PatchRegistry.register(PlayerTabOverlayPatch.class);
        PatchRegistry.register(FriendlyByteBufPatch.class);
        PatchRegistry.register(GuiPatch.class);

        // Compatibility patch for Embeddium/Sodium's BlockOcclusionCache.
        // Always registered so the transformer can catch the class when it
        // first loads. We must NOT use Class.forName() here — that would
        // load the class before our transformer is installed, preventing
        // the patch from ever being applied.
        PatchRegistry.register(BlockOcclusionCachePatch.class);
    }

    public static Minecraft getMcInstance() {
        Minecraft minecraft = null;
        try {
            Class<?> clazz = Minecraft.class;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != clazz) continue;
                field.setAccessible(true);
                minecraft = (Minecraft) field.get(null);
                field.setAccessible(false);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return minecraft != null ? minecraft : Minecraft.getInstance();
    }

}
