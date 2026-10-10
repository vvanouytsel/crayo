# Crayo

An Android app for finding coloring pages and printing them. Type a word (English or Dutch), watch real coloring pages from the web appear, tick the ones you like, and print them on A4.

## How it works

- **Search:** `data/Translate.kt` turns Dutch words into English (built-in word list, then a free online translator). `data/Sources.kt` asks the coloring websites at the same time, and every page found is sent to the screen right away.
- **Checking:** `data/ImageCheck.kt` looks at each picture and checks that it is a coloring page: mostly white paper, no photo colors, and big enough. It is forgiving on purpose. Pictures that fail are hidden behind "Show more" instead of being thrown away.
- **Printing:** `PrintPages.kt` sends one A4 page per drawing to Android's print screen. Wide drawings are turned sideways to fill the page.
- **History:** `data/HistoryStore.kt` remembers every printed page on the phone (the exact picture that was printed), so it can be printed again later without internet.
- **Color example (optional, off by default):** `color/` can print a colored version next to each page. The phone splits the drawing into numbered areas (`Regions.kt`), an OpenAI vision model names a color for each number (`OpenAi.kt`), and the phone paints the original drawing with those colors. The model never draws anything. It needs your own OpenAI key, entered in Settings and stored encrypted on the phone; each page costs a little on that account, and its answer is kept so a reprint is free. An ink setting makes the colors lighter to save ink.
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

## Commits and releases

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org): `type(scope): description`, for example `feat: add print history` or `fix: stop crash when a site is down`.

- `feat:` raises the minor version (1.1.0 → 1.2.0), `fix:` raises the patch version (1.1.0 → 1.1.1), and a `!` after the type (or a `BREAKING CHANGE:` line) raises the major version.
- Other types (`docs`, `chore`, `ci`, `refactor`, `test`, `build`, `perf`, `style`) do not make a release by themselves.
- After the first clone, switch on the local check once: `git config core.hooksPath .githooks`. GitHub also checks every push.
- On every push to `main`, a bot keeps a pull request called "chore(main): release x.y.z" up to date with the new version and changelog. Merging it creates the tag, the GitHub release, and `CHANGELOG.md`.
- The signed app (`crayo.apk`) is built and attached automatically when the repository has two secrets: `CRAYO_KEYSTORE_BASE64` (the key file as text) and `CRAYO_KEYSTORE_PASSWORD`. Without them, build it with `./gradlew assembleRelease` and attach it with `gh release upload vX.Y.Z crayo.apk`.
