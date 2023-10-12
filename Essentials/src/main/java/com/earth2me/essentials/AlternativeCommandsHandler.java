package com.earth2me.essentials;

import java.lang.ref.WeakReference;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class AlternativeCommandsHandler {
    private final transient Map<String, List<WeakReference<Command>>> altcommands = new HashMap<>();
    private final transient Map<String, String> disabledList = new HashMap<>();
    private final transient IEssentials ess;

    public AlternativeCommandsHandler(final IEssentials ess) {
        this.ess = ess;
        for (final Plugin plugin : ess.getServer().getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) {
                addPlugin(plugin);
            }
        }
    }

    public final void addPlugin(final Plugin plugin) {
        if (plugin.getDescription().getMain().contains("com.earth2me.essentials") || plugin.getDescription().getMain().contains("net.essentialsx")) {
            return;
        }
        for (final Map.Entry<String, Command> entry : getPluginCommands(plugin).entrySet()) {
            final String[] commandSplit = entry.getKey().split(":", 2);
            final String commandName = commandSplit.length > 1 ? commandSplit[1] : entry.getKey();
            final Command command = entry.getValue();

            final List<WeakReference<Command>> pluginCommands = altcommands.computeIfAbsent(commandName.toLowerCase(Locale.ENGLISH), k -> new ArrayList<>());
            boolean found = false;

            final Iterator<WeakReference<Command>> iterator = pluginCommands.iterator();
            while (iterator.hasNext()) {
                final WeakReference<Command> next = iterator.next();
                final Command pc2 = next.get();
                if (pc2 == null) {
                    iterator.remove();
                    continue;
                }

                // Safe cast, everything that's added comes from getPluginCommands which already performs the cast check.
                if (((PluginIdentifiableCommand) pc2).getPlugin().equals(plugin)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                pluginCommands.add(new WeakReference<>(command));
            }
        }
    }

    private Map<String, Command> getPluginCommands(Plugin plugin) {
        final Map<String, Command> commands = new HashMap<>();
        for (final Map.Entry<String, Command> entry : ess.getKnownCommandsProvider().getKnownCommands().entrySet()) {
            if (entry.getValue() instanceof PluginIdentifiableCommand && ((PluginIdentifiableCommand) entry.getValue()).getPlugin().equals(plugin)) {
                commands.put(entry.getKey(), entry.getValue());
            }
        }
        return commands;
    }

    public void removePlugin(final Plugin plugin) {
        final Iterator<Map.Entry<String, List<WeakReference<Command>>>> iterator = altcommands.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, List<WeakReference<Command>>> entry = iterator.next();
            entry.getValue().removeIf(pc -> !(pc instanceof PluginIdentifiableCommand) || ((PluginIdentifiableCommand) pc).getPlugin().equals(plugin));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public Command getAlternative(final String label) {
        final List<WeakReference<Command>> commands = altcommands.get(label);
        if (commands == null || commands.isEmpty()) {
            return null;
        }

        if (commands.size() == 1) {
            return commands.get(0).get();
        }

        final Iterator<WeakReference<Command>> iterator = commands.iterator();
        while (iterator.hasNext()) {
            final WeakReference<Command> next = iterator.next();
            final Command command = next.get();
            if (command == null) {
                iterator.remove();
                continue;
            }

            if (command.getName().equalsIgnoreCase(label)) {
                return command;
            }
        }

        // return the first alias
        return commands.get(0).get();
    }

    public void executed(final String label, final Command pc) {
        if (pc instanceof PluginIdentifiableCommand) {
            final String altString = ((PluginIdentifiableCommand) pc).getPlugin().getName() + ":" + pc.getName();
            if (ess.getSettings().isDebug()) {
                ess.getLogger().log(Level.INFO, "Essentials: Alternative command " + label + " found, using " + altString);
            }
            disabledList.put(label, altString);
        }
    }

    public Map<String, String> disabledCommands() {
        return disabledList;
    }
}
