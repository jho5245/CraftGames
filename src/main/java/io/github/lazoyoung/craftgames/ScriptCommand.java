package io.github.lazoyoung.craftgames;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

public class ScriptCommand implements CommandExecutor {
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        src.sendMessage(Text.of("[CraftGames Script Command Help]\n" +
                "/cg script select - Select a script file.\n" +
                "/cg script run - Execute the script."
        ));
        return CommandResult.success();
    }
    
    public CommandSpec get() {
        return CommandSpec.builder()
                .description(Text.of("See usage of script commands.."))
                .child(new SelectScriptCommand().get(), "select", "load")
                .child(new RunScriptCommand().get(), "run", "start", "execute")
                .executor(this)
                .build();
    }
}
