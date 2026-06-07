package com.bekololek.pluginfactory.system.dto;

/**
 * Public system status the web app polls on load: whether the site is in
 * maintenance mode, and the Discord invite to show users while it is.
 */
public record SystemStatusDto(boolean maintenance, String discordUrl) {
}
