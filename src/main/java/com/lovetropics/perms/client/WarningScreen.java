package com.lovetropics.perms.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class WarningScreen extends Screen {
    private static final Component TITLE = Component.literal("Warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

    private final String message;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public WarningScreen(String message) {
        super(TITLE);
        this.message = message;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(TITLE, font);

        LinearLayout contents = layout.addToContents(LinearLayout.vertical()).spacing(10);
        contents.defaultCellSetting().alignHorizontallyCenter().alignVerticallyMiddle();

        FocusableTextWidget text = FocusableTextWidget.builder(Component.literal(message), font)
                .textWidth(250)
                .build();
        contents.addChild(text);

        contents.addChild(SpacerElement.height(Button.DEFAULT_HEIGHT));
        contents.addChild(Button.builder(Component.literal("I understand"), _ -> onClose()).build());

        //This makes it so the FocusableTextWidget can't be focused (it changes appearance when un/focused), so we can use it just for the looks (black on white is clear to see and it looks warning-y)
        layout.visitWidgets(widget -> {
            if (widget == text) {
                addRenderableOnly(widget);
            } else {
                addRenderableWidget(widget);
            }
        });
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, 0, 0, width, height);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(TITLE, Component.literal(message));
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
