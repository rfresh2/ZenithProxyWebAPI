package dev.zenith.web.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Globals;
import com.zenith.command.api.CommandContext;
import com.zenith.discord.EmbedSerializer;
import com.zenith.terminal.logback.LogSourceFilter;
import com.zenith.terminal.logback.TerminalDebugLogFilter;
import com.zenith.util.ComponentSerializer;
import dev.zenith.web.api.model.AuthErrorResponse;
import dev.zenith.web.api.model.CommandRequest;
import dev.zenith.web.api.model.CommandResponse;
import dev.zenith.web.api.model.LogResponse;
import io.javalin.Javalin;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson3;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.zenith.web.WebApiPlugin.LOG;
import static dev.zenith.web.WebApiPlugin.PLUGIN_CONFIG;
import static io.javalin.apibuilder.ApiBuilder.*;

public class WebServer {
    private Javalin server;
    private final Cache<String, Integer> rateLimitCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();
    private final AtomicBoolean logAppenderInitialized = new AtomicBoolean(false);
    private final CircularLogAppender appender = new CircularLogAppender(Math.max(1, PLUGIN_CONFIG.logRetentionEntries));
    private final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    {
        appender.addFilter(new LogSourceFilter());
        appender.addFilter(new TerminalDebugLogFilter());
        encoder.setPattern("[%d{yyyy/MM/dd HH:mm:ss}] [%logger{36}] [%level] %msg%n");
    }

    public synchronized void start() {
        if (server != null) {
            stop();
        }
        if (logAppenderInitialized.compareAndSet(false, true)) {
            startLogAppender();
        }
        server = createServer();
        server.start(PLUGIN_CONFIG.port);
        LOG.info("Web API started on port {}", PLUGIN_CONFIG.port);
        LOG.info("Auth token: {}", PLUGIN_CONFIG.authToken);
        LOG.info("ZenithProxyWebAPI is running on http://localhost:{}", PLUGIN_CONFIG.port);
    }

    private synchronized void startLogAppender() {
        try {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            lc.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
            encoder.setContext(lc);
            appender.setContext(lc);
            encoder.start();
            appender.start();
        } catch (Exception e) {
            LOG.error("Error starting log appender", e);
        }
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
            var objectMapper = JavalinJackson3.defaultMapper();
            config.jsonMapper(new JavalinJackson3(objectMapper, false));
            config.staticFiles.add("/web", Location.CLASSPATH);
            config.routes.apiBuilder(() -> {
                beforeMatched(ctx -> {
                    if (ctx.path().startsWith("/api") || ctx.path().equals("/command")) {
                        String ip = ctx.ip();
                        if (PLUGIN_CONFIG.rateLimiter) {
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
                                rateLimitCache.invalidate(ip);
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
                        LOG.warn("Denied request from {}: {}", ip, reason);
                    } else if (!PLUGIN_CONFIG.webUI) {
                        ctx.status(404);
                        ctx.skipRemainingHandlers();
                    }
                });
                get("/api/logs", ctx -> {
                    long fromIndex = Math.max(0, ctx.queryParamAsClass("from", Long.class).getOrDefault(0L));
                    int requestedLimit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
                    int limit = Math.clamp(requestedLimit, 1, 500);
//                    LOG.debug("{} executed logs, from: {}, limit: {}", ctx.ip(), fromIndex, limit);
                    var snapshot = appender.snapshot(fromIndex, limit);
                    var lines = new ArrayList<String>(snapshot.events().size());
                    for (var event : snapshot.events()) {
                        lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
                    }
                    ctx.json(new LogResponse(
                        snapshot.baseIndex(),
                        snapshot.fromIndex(),
                        snapshot.nextIndex(),
                        snapshot.retained(),
                        lines
                    ));
                    ctx.status(200);
                });
                Handler commandHandler = ctx -> {
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
                };
                post("/command", commandHandler);
                post("/api/command", commandHandler);
            });
        });
    }
}
