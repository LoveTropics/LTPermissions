package com.lovetropics.perms.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WarningScreen extends Screen {
    private final String message;
    private final GridLayout layout = new GridLayout().spacing(15);

    public WarningScreen(String message) {
        super(Component.literal("Warning"));
        this.message = message;
    }

    @Override
    protected void init() {
        GridLayout.RowHelper helper = layout.createRowHelper(1);
        layout.defaultCellSetting().alignHorizontallyCenter();

        helper.addChild(new MultiLineTextWidget(
                Component.literal("Warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), font
        ).setCentered(true), 1);

        helper.addChild(new MultiLineTextWidget(Component.literal(message), font).setCentered(true).setMaxWidth(300), 1);

        helper.addChild(Button.builder(Component.literal("I understand"), _ -> onClose()).build(), 1);

        repositionElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, 0, 0, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
