package com.lovetropics.perms.client;

import net.minecraft.client.Minecraft;

public final class ClientWarningScreen {
    public static void open(String message) {
        Minecraft.getInstance().setScreenAndShow(new WarningScreen(message));
    }
}
