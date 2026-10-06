# Tap Tap — MVP

Android MVP for the Tap Tap monster game.

## MVP
- Starter choice: Light Egg or Dark Egg
- One active creature at a time
- Tap progression: Egg -> Baby -> Mid -> Adult
- Opposite starter unlocks after first hatch
- Pen/collection with active creature selection
- Inactive hatched creatures generate 75% passive coins
- Offline earnings capped at 6 hours
- Tap and passive-income upgrades in +0.5x steps
- Random Egg costs 1,000 coins and must be opened by tapping
- Versioned local save with migrations for safe app updates
- Hybrid system reserved for a later update

## Build
Open in Android Studio with JDK 17 / Android SDK 35 and build the app module.
GitHub Actions builds a debug APK on pushes to main.
