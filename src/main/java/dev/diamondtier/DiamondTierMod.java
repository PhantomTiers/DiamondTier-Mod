package dev.diamondtier;

import net.fabricmc.api.ClientModInitializer;

public class DiamondTierMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierManager.start();
    }
}
