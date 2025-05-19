package dev.zenith.web.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static dev.zenith.web.WebApiPlugin.PLUGIN_CONFIG;
import static dev.zenith.web.WebApiPlugin.SERVER;

public class WebAPICommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("webApi")
            .category(CommandCategory.MODULE)
            .description("""
                Manages the HTTP web API for interacting with this ZenithProxy instance.
                """)
            .usageLines(
                "on/off",
                "port <port>",
                "auth <token>",
                "commandsAccountOwnerPerms on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("webApi").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                if (PLUGIN_CONFIG.enabled) {
                    SERVER.start();
                } else {
                    SERVER.stop();
                }
                c.getSource().getEmbed()
                    .title("Web API " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("port").then(argument("portArg", integer(1, 65535)).executes(c -> {
                PLUGIN_CONFIG.port = getInteger(c, "portArg");
                if (PLUGIN_CONFIG.enabled) {
                    SERVER.start();
                }
                c.getSource().getEmbed()
                    .title("Port Set");
            })))
            .then(literal("auth").then(argument("token", wordWithChars()).executes(c -> {
                PLUGIN_CONFIG.authToken = getString(c, "token");
                c.getSource().getEmbed()
                    .title("Auth Token Set");
            })))
            .then(literal("commandsAccountOwnerPerms").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.commandsAccountOwnerPerms = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Commands Account Owner Perms " + toggleStrCaps(PLUGIN_CONFIG.commandsAccountOwnerPerms));
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Web API", SERVER.isRunning() ? "Running" : "Stopped")
            .addField("Port", PLUGIN_CONFIG.port)
            .addField("Auth Token", PLUGIN_CONFIG.authToken)
            .addField("Commands Account Owner Perms", PLUGIN_CONFIG.commandsAccountOwnerPerms)
            .primaryColor();
    }
}
