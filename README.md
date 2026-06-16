# Blsky Chess ♟️

A chess puzzle trainer that generates tactical puzzles from your own Lichess games.

## Features

- **Fetch 100 games** from any Lichess account via the official API
- **Auto-extract puzzles** - detects checkmates, forks, pins, skewers, discovered attacks, hanging pieces, back-rank weaknesses
- **Interactive chess board** - tap to select and move pieces with legal move highlighting
- **Puzzle tracking** - track solved/failed puzzles across sessions
- **Dark chess UI** - elegant dark theme with gold accents
- **Progress tracking** - difficulty ratings, puzzle types, move counts

## Puzzle Types Detected

| Type | Description |
|------|-------------|
| ♚ Checkmate | Find the forced checkmate |
| ⚔️ Fork | Attack two pieces simultaneously |
| 📌 Pin | Pin a piece to a more valuable one |
| 🗡️ Skewer | Drive a valuable piece to expose another |
| 💡 Discovered Attack | Move one piece to unleash another |
| 🎯 Hanging Piece | Capture an undefended piece |
| 🏰 Back Rank | Exploit back rank weakness |
| 🔗 Combination | Multi-move tactical sequence |

## Build

### Requirements
- Android Studio or CI/CD via GitHub Actions
- Android SDK 24+
- JDK 17

### Build APK
```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### CI/CD
Every push to `main`/`master` triggers a GitHub Actions build that:
1. Compiles the project
2. Builds debug + release APKs
3. Creates a GitHub Release with the APK attached

## Usage

1. Install the APK on your Android device
2. Enter your Lichess username
3. Tap "Fetch Last 100 Games"
4. Tap "Start Puzzle Practice"
5. Solve puzzles from your own games!

## Architecture

```
app/
├── data/
│   ├── Models.kt          # Data classes (Game, Puzzle, Move, Square)
│   ├── LichessApi.kt      # Lichess NDJSON streaming API client
│   └── ChessRepository.kt # Data layer + SharedPreferences persistence
├── engine/
│   └── ChessEngine.kt     # FEN parser, move applier, position evaluator
├── puzzle/
│   └── PuzzleExtractor.kt # Tactical pattern detection
└── ui/
    ├── ChessBoardView.kt  # Custom chess board View
    ├── MainActivity.kt    # Home screen
    ├── PuzzleActivity.kt  # Puzzle solving screen
    ├── GamesListActivity.kt # Puzzle list
    ├── MainViewModel.kt   # Main screen VM
    └── PuzzleViewModel.kt # Puzzle solving VM
```
