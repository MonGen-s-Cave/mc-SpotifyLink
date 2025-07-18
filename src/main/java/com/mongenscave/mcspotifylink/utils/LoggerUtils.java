package com.mongenscave.mcspotifylink.utils;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class LoggerUtils {
    private final Logger logger = LogManager.getLogger("McSpotifyLink");

    public void info(@NotNull String msg, @NotNull Object... objs) {
        logger.info(msg, objs);
    }

    public void warn(@NotNull String msg, @NotNull Object... objs) {
        logger.warn(msg, objs);
    }

    public void error(@NotNull String msg, @NotNull Object... objs) {
        logger.error(msg, objs);
    }

    public void printStartup() {
        String orange = "\\u001B[38;5;208m";
        String reset = "\u001B[0m";
        String software = McSpotifyLink.getInstance().getServer().getName();
        String version = McSpotifyLink.getInstance().getServer().getVersion();

        String asciiArt = orange +
                "  ______    _                _     " + reset +
                orange + " |  ____|  (_)              | |    " + reset +
                orange + " | |__ _ __ _  ___ _ __   __| |___ " + reset +
                orange + " |  __| '__| |/ _ \\ '_ \\ / _` / __|" + reset +
                orange + " | |  | |  | |  __/ | | | (_| \\__ \\" + reset +
                orange + " |_|  |_|  |_|\\___|_| |_|\\__,_|___/" + reset +
                orange + "\n                    |___/                       " + reset;

        info(" ");
        String[] lines = asciiArt.split("\n");

        for (String line : lines) {
            info(line);
        }

        info(" ");
        info("{}   The plugin successfully started.{}", orange, reset);
        info("{}   mc-PlayTime {} {}{}", orange, software, version, reset);
        info("{}   Discord @ dc.mongenscave.com{}", orange, reset);
        info(" ");
    }
}
