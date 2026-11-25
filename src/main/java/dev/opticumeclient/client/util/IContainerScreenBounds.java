package dev.opticumeclient.client.util;

/**
 * Exposes actual container GUI origin coordinates ({@code leftPos}, {@code topPos})
 * from {@code AbstractContainerScreen} for accurate slot hitboxes across double chests,
 * shulker boxes, auction screens, and custom containers.
 */
public interface IContainerScreenBounds {
    int opticume$getLeftPos();

    int opticume$getTopPos();
}
