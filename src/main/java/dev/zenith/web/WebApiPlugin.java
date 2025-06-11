package dev.zenith.web;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import dev.zenith.web.api.WebServer;
import dev.zenith.web.command.WebAPICommand;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = "web-api",
    version = BuildConstants.VERSION,
    description = "Web API for ZenithProxy",
    url = "https://github.com/rfresh2/ZenithProxyWebAPI",
    authors = {"rfresh2"},
    mcVersions = {"1.21.0", "1.21.4", "1.21.5"}
)
public class WebApiPlugin implements ZenithProxyPlugin {
    public static WebAPIConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;
    public static WebServer SERVER;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        PLUGIN_CONFIG = pluginAPI.registerConfig("web-api", WebAPIConfig.class);
        SERVER = new WebServer();
        if (PLUGIN_CONFIG.enabled) {
            SERVER.start();
        }
        pluginAPI.registerCommand(new WebAPICommand());
    }
}
