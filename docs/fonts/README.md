# Dastkhat fonts

Pianova uses the Dastkhat Persian/Latin typeface under the SIL Open Font License 1.1.

Source: https://github.com/dastyar-io/dastkhat-font (FaNum, square-dot style).

The Android font resources are named:
- `dastkhat_regular.ttf` (weight 400)
- `dastkhat_medium.ttf` (weight 500)
- `dastkhat_bold.ttf` (weight 700)

The Gradle `downloadDastkhatFonts` task provisions these files into
`app/src/main/res/font` before Android resource processing when they are absent.
