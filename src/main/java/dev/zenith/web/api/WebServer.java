package dev.zenith.web.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Globals;
import com.zenith.command.api.CommandContext;
import com.zenith.discord.EmbedSerializer;
import com.zenith.util.ComponentSerializer;
import dev.zenith.web.api.model.AuthErrorResponse;
import dev.zenith.web.api.model.CommandRequest;
import dev.zenith.web.api.model.CommandResponse;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.zenith.web.WebApiPlugin.LOG;
import static dev.zenith.web.WebApiPlugin.PLUGIN_CONFIG;

public class WebServer {
    private Javalin server;
    private final Cache<String, Integer> rateLimitCache = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build();

    public synchronized void start() {
        if (server != null) {
            stop();
        }
        server = createServer();
        server.start(PLUGIN_CONFIG.port);
        LOG.info("Web API started on port {}", PLUGIN_CONFIG.port);
        LOG.info("Auth token: {}", PLUGIN_CONFIG.authToken);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
            LOG.info("Web API stopped");
        }
    }

    public synchronized boolean isRunning() {
        return server != null && server.jettyServer().started();
    }

    private Javalin createServer() {
        return Javalin.create(config -> {
                var threadPool = new ExecutorThreadPool();
                threadPool.setDaemon(true);
                threadPool.setName("ZenithProxy-WebAPI-%d");
                config.jetty.threadPool = threadPool;
                config.http.defaultContentType = "application/json";
                var objectMapper = JavalinJackson.defaultMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                config.jsonMapper(new JavalinJackson(objectMapper, false));
            })
            .beforeMatched(ctx -> {
                if (PLUGIN_CONFIG.rateLimiter) {
                    String ip = ctx.ip();
                    synchronized (this) {
                        int reqCount = rateLimitCache.get(ip, () -> 0);
                        rateLimitCache.put(ip, reqCount + 1);
                        if (reqCount >= PLUGIN_CONFIG.rateLimitRequestsPerMinute) {
                            ctx.status(429);
                            ctx.json(new AuthErrorResponse("Rate limit exceeded"));
                            ctx.skipRemainingHandlers();
                            LOG.warn("Rate limit exceeded for IP: {}", ip);
                            return;
                        }
                    }
                }
                var authHeaderValue = ctx.header("Authorization");
                if (authHeaderValue != null) {
                    var expectedHeaderValue = PLUGIN_CONFIG.authToken;
                    if (authHeaderValue.equals(expectedHeaderValue)) {
                        // ok
                        return;
                    }
                }
                String reason = authHeaderValue == null
                    ? "Authorization header missing"
                    : "Invalid auth token";
                ctx.json(new AuthErrorResponse(reason));
                ctx.status(401);
                ctx.skipRemainingHandlers();
                LOG.warn("Denied request from {}: {}", ctx.ip(), reason);
            })
            .post("/command", ctx -> {
                var req = ctx.bodyAsClass(CommandRequest.class);
                var command = req.command();
                var context = CommandContext.create(command, WebAPICommandSource.INSTANCE);
                LOG.info("{} executed command: {}", ctx.ip(), command);
                Globals.COMMAND.execute(context);
                context.getSource().logEmbed(context, context.getEmbed());
                String embedResponse = null;
                String embedResponseComponent = null;
                List<String> multiLineResponse = context.getMultiLineOutput();
                if (context.getEmbed().isTitlePresent()) {
                    var embedComponent = EmbedSerializer.serialize(context.getEmbed());
                    embedResponse = ComponentSerializer.serializePlain(embedComponent);
                    embedResponseComponent = ComponentSerializer.serializeJson(embedComponent);
                }
                ctx.json(new CommandResponse(embedResponse, embedResponseComponent, multiLineResponse));
                ctx.status(200);
            });
    }
}
