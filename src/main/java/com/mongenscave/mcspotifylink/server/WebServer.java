package com.mongenscave.mcspotifylink.server;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.handlers.CallbackHandler;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class WebServer {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();
    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(ConfigKeys.SPOTIFY_REDIRECT_URI_PORT.getInt()), 0);
            server.createContext("/callback", new CallbackHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
}
