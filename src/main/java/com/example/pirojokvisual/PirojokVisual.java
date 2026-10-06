package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;

public class PirojokVisual implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        new Visuals().onInitializeClient();
    }
}
