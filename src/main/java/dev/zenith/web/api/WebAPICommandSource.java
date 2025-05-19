package dev.zenith.web.api;

import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandOutputHelper;
import com.zenith.command.api.CommandSource;
import com.zenith.discord.Embed;

import static dev.zenith.web.WebApiPlugin.PLUGIN_CONFIG;

public class WebAPICommandSource implements CommandSource {
    public static final WebAPICommandSource INSTANCE = new WebAPICommandSource();

    @Override
    public String name() {
        return "WebAPI";
    }

    @Override
    public boolean validateAccountOwner(final CommandContext ctx) {
        if (PLUGIN_CONFIG.commandsAccountOwnerPerms) {
            return true;
        } else {
            ctx.getEmbed()
                .description("Web API is not authorized to execute this command!");
            return false;
        }
    }

    @Override
    public void logEmbed(final CommandContext commandContext, final Embed embed) {
        CommandOutputHelper.logEmbedOutputToTerminal(embed);
    }
}
