package com.mongenscave.mcspotifylink;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.mongenscave.mcspotifylink.config.Config;
import com.mongenscave.mcspotifylink.database.Database;
import com.mongenscave.mcspotifylink.database.impl.H2;
import com.mongenscave.mcspotifylink.database.impl.JSON;
import com.mongenscave.mcspotifylink.database.impl.MySQL;
import com.mongenscave.mcspotifylink.hooks.plugins.PlaceholderAPI;
import com.mongenscave.mcspotifylink.listeners.MenuListener;
import com.mongenscave.mcspotifylink.listeners.SpotifyListener;
import com.mongenscave.mcspotifylink.manager.SpotifyManager;
import com.mongenscave.mcspotifylink.manager.TimestampManager;
import com.mongenscave.mcspotifylink.server.WebServer;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import com.mongenscave.mcspotifylink.utils.RegisterUtils;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import revxrsal.zapper.ZapperJavaPlugin;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class McSpotifyLink extends ZapperJavaPlugin {
    @Getter private static McSpotifyLink instance;
    @Getter private Config language;
    @Getter private Config guis;
    @Getter private TaskScheduler scheduler;
    @Getter private Database database;
    @Getter WebServer webServer;
    @Getter SpotifyManager spotifyManager;
    @Getter TimestampManager timestampManager;

    private Config config;

    @Override
    public void onLoad() {
        instance = this;
        scheduler = UniversalScheduler.getScheduler(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeComponents();
        initializeDatabase();

        spotifyManager = new SpotifyManager();
        webServer = new WebServer();
        timestampManager = new TimestampManager();

        getServer().getPluginManager().registerEvents(new SpotifyListener(), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        webServer.start();
        RegisterUtils.startUpdateTask();
        RegisterUtils.registerCommands();
        PlaceholderAPI.registerHook();
    }


    @Override
    public void onDisable() {
        CompletableFuture<Void> shutdownFuture = CompletableFuture.completedFuture(null);

        if (database != null) {
            shutdownFuture = database.close()
                    .exceptionally(throwable -> {
                        LoggerUtils.error("Error closing database: " + throwable.getMessage());
                        return null;
                    });
        }

        shutdownFuture.thenRun(() -> {
            if (spotifyManager != null) spotifyManager.shutdown();
            if (webServer != null) webServer.stop();
            if (timestampManager != null) timestampManager.stopTimestampUpdates();
        });

        try {
            shutdownFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            LoggerUtils.error("Error during shutdown: " + exception.getMessage());
        }
    }

    public Config getConfiguration() {
        return config;
    }

    private void initializeComponents() {
        final GeneralSettings generalSettings = GeneralSettings.builder()
                .setUseDefaults(false)
                .build();

        final LoaderSettings loaderSettings = LoaderSettings.builder()
                .setAutoUpdate(true)
                .build();

        final UpdaterSettings updaterSettings = UpdaterSettings.builder()
                .setKeepAll(true)
                .build();

        config = loadConfig("config.yml", generalSettings, loaderSettings, updaterSettings);
        language = loadConfig("messages.yml", generalSettings, loaderSettings, updaterSettings);
        guis = loadConfig("guis.yml", generalSettings, loaderSettings, updaterSettings);
    }

    @NotNull
    @Contract("_, _, _, _ -> new")
    private Config loadConfig(@NotNull String fileName, @NotNull GeneralSettings generalSettings, @NotNull LoaderSettings loaderSettings, @NotNull UpdaterSettings updaterSettings) {
        return new Config(
                new File(getDataFolder(), fileName),
                getResource(fileName),
                generalSettings,
                loaderSettings,
                DumperSettings.DEFAULT,
                updaterSettings
        );
    }

    private void initializeDatabase() {
        String host;
        int port;
        String databaseName, username, password;
        int poolSize;

        String databaseType = this.config.getString("database.type").toLowerCase();

        switch (databaseType) {
            case "mysql" -> {
                host = this.config.getString("database.mysql.host");
                port = this.config.getInt("database.mysql.port");
                databaseName = this.config.getString("database.mysql.database");
                username = this.config.getString("database.mysql.username");
                password = this.config.getString("database.mysql.password");
                poolSize = this.config.getInt("database.mysql.pool-size");
                this.database = new MySQL(host, port, databaseName, username, password, poolSize);
            }
            case "h2" -> this.database = new H2();
            case "json" -> this.database = new JSON();

            default -> {
                LoggerUtils.error("Unsupported database type: " + databaseType);
                getServer().getPluginManager().disablePlugin(this);
            }
        }
    }
}