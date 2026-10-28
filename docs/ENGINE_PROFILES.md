# Native engine profiles in Janggi Lab 1.3.x

The existing board, piece editor, analysis, matches, history, and four
Janggi Lab variants are unchanged. The app's rules are **always adjudicated
by its bundled patched Fairy-Stockfish referee** using `appstate`; search-only
engines are separate child processes speaking UCI. An external engine is not
trusted to decide legal moves or the result. Its `bestmove` is checked against
the referee's legal move list before the UI commits it.

## Engine menu

In Settings → 초 A · 탐색 엔진 / 한 B · 탐색 엔진:

| UI selection | Native file | Android targets | Notes |
| --- | --- | --- | --- |
| Fairy-Stockfish | `libfairy.so` | ARMv7, ARM64, x86_64 | Always supplied; existing engine |
| FSF DOTPROD | `libfairy_dotprod.so` | ARM64 only | ARMv8.2-A dot-product build; CPU flag check before execution |
| Stockless Variants | `libstockless.so` | ARM64 only | Pinned hybrid engine, separate UCI process |
| User-built UCI | `libcustom.so` | ABI matching the APK | Developer-supplied pre-bundled variant engine; requires protocol verification |

Options remain visible if not bundled but explicitly show `사용 불가` and
are rejected when selected. **A random UCI chess binary is insufficient**:
the engine must support Janggi 9×10 coordinates and the required rules,
or the custom `VariantConfigPath`/`VariantName` mechanism.

The stock Android sandbox may prohibit executing ELF files copied to
ordinary app-data or Downloads folders. Accordingly this release **does not**
promise that opening a downloaded executable via the file picker is safe or
supported. Instead, external engines are packaged as APK native libraries
at build time with `android:extractNativeLibs=true`, then launched as
standalone child processes via Android's linker when needed.

## Preserving Janggi rules across engines

Four alternate definitions in `app/assets/janggilab-variants.ini` mirror
the referee's `native/Fairy-Stockfish/src/variants.ini`.
Stockless loads this precise INI through `VariantConfigPath`, then selects
one of `janggilabtraditional`, `janggilabmodern`,
`janggilabnopass` or `janggilabblitz` using `VariantName`.
This prevents silently using a similarly named upstream mode with different
material-counting or repetition rules.

To switch variants or profiles, the app starts **new** referee and search
processes, checks initial positions and the UCI handshake, and only then
atomically replaces the old processes. Failure leaves the previous processes
and move history in place. The *user-requested* change of rules resets the
current game's history, as before.

## NNUE compatibility

The existing NNUE file picker still copies an imported FSF Janggi `.nnue`
file to a private `janggi-*.nnue` path. In the default engine it uses the
existing `EvalFile` + `Use NNUE` validation. In external engines it must
also receive a positive NNUE evaluation status before the change commits.

Stockless Variants at pinned SHA
`99067d0964e40285dd5c24de9c0ecb666dc31924` contains the original
Fairy-Stockfish NNUE loader. This is **distinct** from its experimental
`.svnnue` format; no format conversion is attempted.
See [its FSF network instructions](https://github.com/gaong5247-cmd/Stockless-variants/blob/main/docs/FSF_NNUE.md).
The default APK does not redistribute the original FSF neural weights.

NNUE is applied only to processes that actually perform search; unused
Fairy arbiters remain on classical evaluation to avoid redundant NNUE memory.

## Failure containment

- Every external engine runs in its own process with a bounded UCI output
  queue; engine exit, malformed bestmoves, bad NNUE activation, and
  response timeouts become errors instead of trusting corrupt state.
- An engine crash cannot directly corrupt the Java chessboard: the move
  history remains saved via SharedPreferences.
- On search failure the app attempts to restart the engines, falling back
  to built-in Fairy if the chosen engine is unavailable and then to classical
  evaluation if the saved NNUE also cannot load.
- Repeated failures pause automatic recovery after two attempts. The user
  can choose `복구 다시 시도` manually.
- No implementation can guarantee that an engine will **never** crash:
  on-device tests are required for SIGILL, low-memory kills, suspend/resume,
  and vendor-specific linker restrictions.

## Building

GitHub Actions builds standard APKs for ARMv7, ARM64, x86_64 and a Universal
APK. ARM64 also includes a separate `-march=armv8.2-a+dotprod` Fairy
binary. The Android job builds the exact pinned Stockless source using
its materializer and bundled dependency commits, and packages it as
`libstockless.so`. The build fails if a mandatory included file does
not pass the ELF/architecture/page-alignment checks.

Termux builds baseline + DOTPROD Fairy automatically. You can include an
already compiled Android ARM64 Stockless engine or another compatible native
engine at APK packaging time:

```sh
STOCKLESS_ARM64_ELF=/absolute/path/to/stockless-variants \
CUSTOM_ARM64_ELF=/absolute/path/to/custom-janggi-engine \
python3 scripts/package-termux.py arm64-v8a
```

Only set variables for engines you actually have; the Python builder refuses
non-ELF files. The custom engine must implement the documented Janggi UCI
contract. Build-time inclusion does **not** prove runtime support.

The Stockless binary uses AGPLv3; see the APK's
`THIRD_PARTY_NOTICES.txt`, `AGPL-3.0.txt`, and the [pinned upstream
repository](https://github.com/gaong5247-cmd/Stockless-variants/tree/99067d0964e40285dd5c24de9c0ecb666dc31924)
for corresponding source and generation instructions.

## Testing boundaries

CI host Java suites verify the existing Fairy `appstate` behavior, UCI
search adapter and exact Stockless custom variants; a separate mock-engine
suite injects malformed moves and an abrupt process exit. The Android
packaging job verifies executable ELF architecture, 16-KiB page alignment,
classes, assets, and package signing. **The results do not establish
runtime success on a physical Android phone.** In particular, NNUE on
Stockless with a Janggi Lab custom variant needs a real-network, on-device
test before making a stronger compatibility claim.
