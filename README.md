# Crayo

An Android app for finding coloring pages and printing them. Type a word (English or Dutch), watch real coloring pages from the web appear, tick the ones you like, and print them on A4.

## How it works

- **Search:** `data/Translate.kt` turns Dutch words into English (built-in word list, then a free online translator). `data/Sources.kt` asks the coloring websites at the same time, and every page found is sent to the screen right away.
- **Checking:** `data/ImageCheck.kt` looks at each picture and checks that it is a coloring page: mostly white paper, no photo colors, and big enough. It is forgiving on purpose. Pictures that fail are hidden behind "Show more" instead of being thrown away.
- **Printing:** `PrintPages.kt` sends one A4 page per drawing to Android's print screen. Wide drawings are turned sideways to fill the page.
- **Screen:** `MainActivity.kt` (Jetpack Compose) works on phones and tablets.

## Websites used

supercoloring.com (searched 8 different ways), mondaymandala.com, bestcoloringpagesforkids.com, coloringpagesfortoddlers.com, lovelycoloring.com, freecoloringpages.net, and openclipart.org.

To add another coloring blog, add its domain to `articleSites` in `Sources.kt`.

Crayo is for printing at home. Drawings belong to their makers; please do not republish them.

## Build it

You need Java 21 and the Android SDK (platform 37). Point `local.properties` at the SDK (`sdk.dir=...`), then:

```
./gradlew assembleDebug
```

The app is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Release build and installing on a phone

The signed app is built with `./gradlew assembleRelease`. The signing key is **not** in this repository: keep a backup of it somewhere safe, because without it installed apps cannot be updated. The build looks for two Gradle properties (put them in `~/.gradle/gradle.properties`):

```
crayoKeystore=<path to the .jks file>
crayoKeystorePassword=<its password>
```

Download `crayo.apk` from the latest release on GitHub, open it on the phone, and allow installing from that source once.
