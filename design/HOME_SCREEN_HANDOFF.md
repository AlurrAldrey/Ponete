# Home screen – design handoff (Android / Kotlin)

The file `home-screen-preview.html` next to this one is the design's reference source. It's HTML/JS from the design tool, **not code to ship**. Read it for exact values and behavior, then rebuild the screen natively.

## Prompt to paste into Claude Code

> Read `design/HOME_SCREEN_HANDOFF.md` and `design/home-screen-preview.html`. Implement this home screen in our Android app, following the conventions already in this codebase (Jetpack Compose or XML Views, whichever we use; our navigation, theming and data layer). First explore the project and tell me your plan and which files you'll touch, then implement. Wire "Random" and "Colecciones" to our existing actions if they exist, otherwise leave TODOs. The album count and covers must come from our real data.

## Layout (design frame 390×844 dp)

- Full-screen animated pixel background (below).
- Title "PONETE / ALGO, / GORDO!" (3 lines), font **Jersey 10** (Google Fonts, OFL), 116sp, line-height 0.75, left-aligned, 20dp side padding, ~56dp from top.
- 24dp below: **Random** primary button, full width (341dp), 76dp tall, fully rounded, shuffle icon + label 46sp Jersey 10, letter-spacing 0.04em.
  - It has a 5dp outline ring that shows the *inverted* color of whatever is behind it (Difference blend of white).
- 16dp below: **Colecciones** secondary button, full width, 64dp tall, fully rounded, 3dp border, stacked-cards icon + label 36sp.
- Album section **fills all the remaining height** below the buttons (24dp top padding, 40dp bottom padding): label "**[N] ÁLBUMES**" (30sp, letter-spacing 0.06em, same color as the title), then 12dp below an auto-scrolling strip of square album covers (10dp corners, 12dp gap) moving left in a seamless loop (~24s per full set). Decorative only: no touch.
  - **Covers are sized dynamically:** each cover's side = the height left for the strip, so taller phones get bigger covers. In Compose: give the section `Modifier.weight(1f)`, measure the strip with `BoxWithConstraints` and use `maxHeight` as the cover size (or `fillMaxHeight().aspectRatio(1f)` on each cover). The header and buttons keep their natural size and never shrink.

## Themes (cycle in this order, loop forever)

Each theme holds **5s**, then crossfades to the next over **2.5s** (smoothstep easing). All colors interpolate together: background palette, title, label and both buttons.

| # | Name | Background palette (10 steps, used cyclically) | Title + label | Random bg / text | Colecciones bg / text / border |
|---|---|---|---|---|---|
| 1 | Night neon | #0B0420 #160A3A #24105A #3A1470 #5B1A9E #7A1FD0 #5B1A9E #3A1470 #24105A #160A3A | #C6FF3D | #C6FF3D / #0B0420 | #0B0420 / #C6FF3D / #C6FF3D |
| 2 | Sunset pop | #FF3D2E #FF5A24 #FF7A1A #FF9A14 #FFB81A #FFD84D #FFB81A #FF9A14 #FF7A1A #FF5A24 | #2A0A4A | #2A0A4A / #FFE9A8 | #FFF4D6 / #2A0A4A / #2A0A4A |
| 3 | Acid mint | #00C2FF #00D4E0 #00E0B8 #3DF5A0 #7CFF7A #A6FF5C #7CFF7A #3DF5A0 #00E0B8 #00D4E0 | #000000 | #000000 / #A6FF5C | #FFFFFF / #000000 / #000000 |
| 4 | Electric blue | #03102E #061C4A #0A2A6E #0F3D99 #1752CC #1E6BFF #1752CC #0F3D99 #0A2A6E #061C4A | #FFFFFF | #FFFFFF / #0F3D99 | #03102E / #FFFFFF / #FFFFFF |
| 5 | Neon violet | #7B3CFF #9D4BFF #C24DFF #E040FB #FF3DCB #FF5AA0 #FF3DCB #E040FB #C24DFF #9D4BFF | #1E1E1E | #FEFEFE / #000000 | #1E1E1E / #FEFEFE / #000000 |

## Pixel background

- Grid of square cells, **15dp** each (26×57 cells on the 390×844 frame). Draw each cell as a filled rect; no smoothing.
- Every frame, with `s = seconds × (40/120)` and moving center `cx = w·(0.5 + 0.45·sin(0.7s))`, `cy = h·(0.5 + 0.4·cos(0.53s))`:
  ```
  v = sin(0.18x + 1.3s) + sin(0.11y − 0.9s) + sin(0.12x + 0.08y + 1.1s) + sin(0.28·dist((x,y),(cx,cy)) − 1.7s)
  n = (v + 4) / 8
  index = floor(n · P · 2 + 0.8s + push) mod P      // P = 10, positive modulo
  color = blendedPalette[index]
  ```
- On top, the whole background slowly sways in hue between −15° and +15° (12s each way, ease-in-out). On Android: a hue-rotation `ColorMatrix` applied to the background layer.
- **Tap wave:** tapping Random starts a ring from the button's center. Radius grows to the screen diagonal in ~1.1s while fading out over 1.3s. Up to 4 overlapping waves. For each cell: `push += exp(−d²/6) · fade · 5`, where `d = distance(cell, origin) − radius` (in cells).
- **Tap also skips to the next theme:** the theme cycle runs on its own clock. A Random tap fast-forwards that clock to the moment the next theme is fully in, over **0.8s** (smoothstep), so the crossfade happens quickly; then normal timing resumes from there. Taps during a skip still make a wave but don't queue another skip. See `skipToNextTheme()` / `clock()` in `home-screen-preview.html`.
- Compose hint: `Canvas` + `withFrameNanos` loop. Precompute the 10 blended colors once per frame, not per cell.

## Accessibility

- Respect "Remove animations" (animator duration scale = 0): show theme 1 statically, with no marquee and no hue sway.
- Background and album strip are decorative (not announced by screen readers). Buttons keep their text labels; the icons are decorative.
- Text color can be briefly low-contrast during the 2.5s fades; that's expected.
