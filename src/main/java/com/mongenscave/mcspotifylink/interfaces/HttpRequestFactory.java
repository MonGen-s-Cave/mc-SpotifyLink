package com.mongenscave.mcspotifylink.interfaces;

import org.apache.http.client.methods.HttpUriRequest;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface HttpRequestFactory {
    HttpUriRequest create(@NotNull String url);
}
