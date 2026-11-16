package de.z0rdak.yawp.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.authlib.services.response.ErrorResponse;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.players.UserNameToIdResolver;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static de.z0rdak.yawp.constants.Constants.LOGGER;

public class MojangApiHelper {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapper.create();

    private MojangApiHelper() {
    }

    public static void getGameProfileInfo(UUID uuid, Consumer<@Nullable GameProfile> onResult) {
        GameProfile profile = null;
        try {
            String uuidWithOutDashes = uuid.toString().replace("-", "");
            String uri = "https://api.minecraftservices.com/minecraft/profile/lookup/" + uuidWithOutDashes;
            profile = fetchGameProfileInfo(uri);
        } catch (Exception e) {
            LOGGER.error("Error fetching game profile info for player '{}': {}", uuid.toString(), e);
        }
        onResult.accept(profile);
    }

    public static void getGameProfileInfo(String username, Consumer<@Nullable  GameProfile> onResult) {
        GameProfile profile = null;
        try {
            String uri = "https://api.mojang.com/users/profiles/minecraft/" + username;
            profile = fetchGameProfileInfo(uri);
        } catch (Exception e) {
            LOGGER.error("Error fetching game profile info for player '{}': {}", username, e);
        }
        onResult.accept(profile);
    }

    /**
     * Referenced: {@link com.mojang.authlib.minecraft.client.MinecraftClient#readServiceResponse(URL, Class, HttpURLConnection, String)}
     */
    private static GameProfile fetchGameProfileInfo(String uri) {
        try {
            HttpURLConnection connection = createUrlConnection(new URI(uri).toURL());
            InputStream inputStream = null;
            try {
                final int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_FOUND) {
                    return null;
                }

                final String result;
                if (status < 400) {
                    inputStream = connection.getInputStream();
                    result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    if (result.isEmpty()) {
                        return null;
                    }
                    // Lookup endpoints return identity only. GameProfile's properties are required in authlib 10.
                    var identity = OBJECT_MAPPER.readValue(result, com.mojang.authlib.services.response.NameAndId.class);
                    if (identity == null || identity.id() == null || identity.name() == null || identity.name().isBlank()) {
                        return null;
                    }
                    return new GameProfile(identity.id(), identity.name());
                } else {
                    final String contentType = connection.getContentType();
                    inputStream = connection.getErrorStream();
                    final ErrorResponse errorResponse;
                    if (inputStream != null) {
                        result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                        if (contentType != null && contentType.startsWith("text/html")) {
                            throw new MinecraftClientHttpException(status);
                        }
                        try {
                            errorResponse = OBJECT_MAPPER.readValue(result, ErrorResponse.class);
                        } catch (RuntimeException invalidErrorBody) {
                            throw new MinecraftClientHttpException(status);
                        }
                        throw new MinecraftClientHttpException(status, errorResponse);
                    } else {
                        throw new MinecraftClientHttpException(status);
                    }
                }
            } catch (final IOException e) {
                //Connection errors
                throw new MinecraftClientException(
                    MinecraftClientException.ErrorType.SERVICE_UNAVAILABLE , "Failed to read from " + uri + " due to " + e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(inputStream);
                connection.disconnect();
            }
        } catch (URISyntaxException|MalformedURLException e) {
            LOGGER.error("Could not create URL of {}", uri);
            return null;
        }
    }

    public static Optional<GameProfile> lookupGameProfileInCache(CommandContext<CommandSourceStack> ctx, String playerName) {
        // A name-cache miss may query the name lookup API; callers must run this off the server thread.
        return lookupGameProfileInCache(ctx.getSource().getServer().services().nameToIdCache(), playerName);
    }

    public static Optional<GameProfile> lookupGameProfileInCache(CommandContext<CommandSourceStack> ctx, UUID uuid) {
        return lookupGameProfileInCache(ctx.getSource().getServer().services().nameToIdCache(), uuid);
    }

    public static Optional<GameProfile> lookupGameProfileInCache(UserNameToIdResolver resolver, String playerName) {
        return resolver.get(playerName).map(identity -> new GameProfile(identity.id(), identity.name()));
    }

    public static Optional<GameProfile> lookupGameProfileInCache(UserNameToIdResolver resolver, UUID uuid) {
        return resolver.get(uuid).map(identity -> new GameProfile(identity.id(), identity.name()));
    }

    /**
     * Referenced: {@link com.mojang.authlib.minecraft.client.MinecraftClient#createUrlConnection(URL)}
     */
    private static HttpURLConnection createUrlConnection(URL url) {
        try {
            LOGGER.debug("Connecting to {}", url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            return connection;
        } catch (IOException io) {
            throw new MinecraftClientException(MinecraftClientException.ErrorType.SERVICE_UNAVAILABLE, "Failed connecting to " + url, io);
        }
    }
}
