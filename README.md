# Tap Tap — V1 Beta

Android V1 Beta for the Tap Tap monster game.

## MVP gameplay
- Starter choice: Light Egg or Dark Egg
- One active creature at a time
- Tap progression: Egg -> Baby -> Mid -> Adult
- Opposite starter unlocks after first hatch
- Collection cycling with active creature selection
- Inactive hatched creatures generate 75% passive coins
- Offline earnings capped at 6 hours
- Tap and passive-income upgrades in +0.5x steps
- Random Egg costs 1,000 coins and must be opened by tapping
- Versioned local save with migrations for safe app updates

## Visuals
- Native Android vector assets for Light: Egg / Baby / Mid / Adult
- Native Android vector assets for Dark: Egg / Baby / Mid / Adult
- Hybrid Egg / Baby / Mid / Adult assets are bundled for a later gameplay update
- Branded split Light/Dark Tap Tap launcher icon
- Dedicated monochrome notification icon

## Notifications
- TAP TAP · Egg hatched
- TAP TAP · Offline rewards
- TAP TAP · Update available
- Tap opens the game
- Separate Android notification channels for Progress, Rewards and Updates

## Build
Codemagic workflow: **Tap Tap V1 Beta APK**

Version: **1.0.0-beta**

Output artifact: **TapTap-v1-beta.apk**

The project targets Android SDK 35, Java 17, and uses Codemagic for APK builds.
