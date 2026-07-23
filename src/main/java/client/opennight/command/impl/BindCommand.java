package client.opennight.command.impl;

import com.mojang.blaze3d.platform.InputConstants;
import client.opennight.NightClient;
import client.opennight.command.Command;
import client.opennight.event.EventTarget;
import client.opennight.exception.ModuleNotFoundException;
import client.opennight.modules.Module;
import client.opennight.utils.misc.ChatUtil;

public class BindCommand
extends Command {
    public static final class EventHandler {
        private final BindCommand parent;
        private final Module module;
        private final String name;

        public EventHandler(BindCommand parent, Module module, String name) {
            this.parent = parent;
            this.module = module;
            this.name = name;
        }

        @EventTarget
        public void onKey(client.opennight.event.impl.KeyEvent event) {
            if (!event.isPressed()) return;
            int keyCode = event.getKeyCode();
            if (keyCode == 256) {
                module.setKey(0);
                ChatUtil.print("Unbound " + this.name + ".");
            } else {
                module.setKey(keyCode);
                ChatUtil.print("Bound " + this.name + " to key " + keyCode + ".");
            }
            NightClient.getInstance().getEventBus().unregister(this);
            NightClient.getInstance().getConfigManager().saveAll();
        }
    }

    public BindCommand() {
        super("bind", new String[]{"b"});
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length == 1) {
            this.handleBindInteractive(args[0]);
        } else if (args.length == 2) {
            this.handleBindExplicit(args[0], args[1]);
        } else {
            ChatUtil.print("Usage: .bind <module> [key]");
        }
    }

    private void handleBindInteractive(String moduleName) {
        try {
            Module module = NightClient.getInstance().getModuleManager().getModule(moduleName);
            if (module == null) {
                ChatUtil.print("Invalid module.");
                return;
            }
            ChatUtil.print("Press a key to bind " + moduleName + " to.");
            NightClient.getInstance().getEventBus().register(new BindCommand.EventHandler(this, module, moduleName));
        } catch (ModuleNotFoundException e) {
            ChatUtil.print("Invalid module.");
        }
    }

    private void handleBindExplicit(String moduleName, String keyName) {
        try {
            Module module = NightClient.getInstance().getModuleManager().getModule(moduleName);
            if (module == null) {
                ChatUtil.print("Invalid module.");
                return;
            }
            if (keyName.equalsIgnoreCase("none")) {
                module.setKey(InputConstants.UNKNOWN.getValue());
                ChatUtil.print("Unbound " + moduleName + ".");
                NightClient.getInstance().getConfigManager().saveAll();
                return;
            }
            InputConstants.Key key = InputConstants.getKey("key.keyboard." + keyName.toLowerCase());
            if (key == InputConstants.UNKNOWN) {
                ChatUtil.print("Invalid key.");
                return;
            }
            module.setKey(key.getValue());
            ChatUtil.print("Bound " + moduleName + " to " + keyName.toUpperCase() + ".");
            NightClient.getInstance().getConfigManager().saveAll();
        } catch (ModuleNotFoundException e) {
            ChatUtil.print("Invalid module.");
        }
    }

    @Override
    public String[] onTab(String[] stringArray) {
        return NightClient.getInstance().getModuleManager().getModules().stream().map(Module::getName).filter(string -> string.toLowerCase().startsWith(stringArray.length == 0 ? "" : stringArray[0].toLowerCase())).toArray(String[]::new);
    }
}