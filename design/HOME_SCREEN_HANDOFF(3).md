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

## Two states: Home and Album selected

| | Home | Album selected |
|---|---|---|
| Title | "PONETE / ALGO, / GORDO!" at 116sp | The picked album's name, auto-sized |
| Big button (with ring) | shuffle icon + **Random** → picks an album | play icon + **Escuchar** → play the album (TODO: wire to the player) |
| Outlined button | stacked-cards icon + **Colecciones** → collections (TODO) | shuffle icon + **Otro** → picks another album |
| Background, title and button colors | the 5-theme cycle | **taken from the album's cover art** (see below) |
| Album strip + "[N] ÁLBUMES" | visible | hidden |
| Selected cover | hidden | visible, between title and buttons |

Every pick (Random in Home, Otro in Selected) also sends the **wave** from the tapped button. There is **no on-screen back button**: Android's system back gesture/button returns to Home while an album is selected (use `BackHandler(enabled = state == Selected)` in Compose). In the preview, **Esc** stands in for it.

### Typewriter text (title and both button labels)

All text changes between states use the same typewriter:
1. **Erase** one character at a time from the end, **28ms per character**.
2. When empty, apply the new size / icon.
3. **Type** the new text one character at a time, **60ms per character**.

- **Title:** before typing, pick the largest font size (116sp down to 40sp, in 4sp steps) at which the album name fits the 341×291dp title box without breaking words. Going back Home always restores 116sp. A blinking block cursor `▌` (0.7s blink) follows the title text while it erases/types.
- **Button labels:** Random ↔ Escuchar and Colecciones ↔ Otro, run at the same time as the title. Each button's icon swaps at the moment its label is empty (shuffle ↔ play, stacked cards ↔ shuffle).
- Any new action (another pick, or the back gesture) cancels running typewriters and starts from whatever text is showing.
- Accessibility: announce only the final result once (album name, or "Inicio" when going back), never letter by letter. In Compose: `semantics { liveRegion = LiveRegionMode.Polite }` on a text set once at the end. Buttons keep proper labels for screen readers even mid-animation (use `contentDescription` = final label).

### Home → Album selected (first pick)

All start together:
- **Colors blend to the album's palette** over **0.9s** (smoothstep), see "Colors from the album cover".
- **Buttons slide down** to the bottom (Colecciones ends 40dp above the bottom edge; buttons block starts at 648dp in the 844 design). 0.7s, same easing.
- **Album strip + label drop and fade out:** 160dp down, fade to 0 over 0.4–0.5s, then stop taking touches.
- **Cover emerges** after **350ms**: scales 0.4→1 with slight overshoot (`cubic-bezier(.3,1.5,.5,1)`, 0.55s) and fades in (0.4s). 16dp corners, hard drop shadow (0, 10dp, black 25%). Decorative for screen readers, and it must **never block touches** while hidden.
- **Cover placement follows the title's actual text height:** cover top = bottom of title text + 24dp; side = space left down to the buttons − 24dp, capped at 341dp, minimum 80dp, centered horizontally. Recompute on every pick.

### Otro (later picks)
The layout stays in the selected state. The cover shrinks and fades out (0.25s), the new cover pops in after **250ms** and the colors blend to the new album's palette (0.9s), and the title retypes. The button labels don't change.

### Back gesture (Album selected → Home)
Everything reverses: the cover shrinks out, the colors blend back into the live theme cycle (0.9s), the title retypes "PONETE / ALGO, / GORDO!" at 116sp, the labels retype to Random / Colecciones with their original icons, the buttons slide back up, and the album strip rises and fades back in. No wave. Move focus to the Random button.

### Notes
- The wave and the difference-blend ring on the big button must keep working while the buttons move. In the web preview the buttons move with `top`, not `transform`, for this reason. In Compose, draw the ring so its blend still reads the background (not inside an offscreen layer of the button).
- Album names and cover colors in the preview are placeholders; use the real collection and artwork.
- Reference: `pickAlbum()`, `goHome()`, `paletteFromArt()`, `currentTheme()`, `typewrite()`, `retitle()`, `relabel()` and `fitTitle()` in `home-screen-preview.html`.

## Colors from the album cover

While an album is selected, the whole screen takes its colors from that album's cover art instead of the theme cycle:
- **Background ramp:** pull 4 representative colors from the cover, sort them dark → light, spread them into 6 stops, and mirror them into the same 10-step ping-pong ramp the home themes use (`[0,1,2,3,4,5,4,3,2,1]`).
- **Title, label and outlines:** near-white `#FFFFFF` or near-black `#111111`, whichever has the better *worst-case* contrast against all 10 ramp colors.
- **Big button:** filled with the title color. Its label/icon uses the most saturated cover color that has ≥ 4.5:1 contrast with that fill (fallback: the opposite of the title color).
- **Outlined button:** filled with the cover's darkest color (when text is white) or lightest (when text is black), with text and border in the title color.
- **Transitions:** blend from whatever is currently showing to the album palette over **0.9s** (smoothstep), both on the first pick and between albums. On back, blend from the album palette into the *live* cycle over 0.9s, then hand back to the normal cycle.
- **On Android, use `androidx.palette:palette-ktx`** instead of the preview's k-means: `Palette.from(bitmap).maximumColorCount(16).generate()` off the main thread (the cover is usually already loaded by Coil/Glide; use its bitmap). Take the populated swatches among `darkMutedSwatch, darkVibrantSwatch, mutedSwatch, vibrantSwatch, lightVibrantSwatch, lightMutedSwatch`, sort by luminance (`ColorUtils.calculateLuminance`), keep 4 (fill gaps by interpolating), and build the ramp and text/button colors with the rules above. `ColorUtils.calculateContrast` gives the contrast ratios. Cache the result per album.
- The preview's placeholder covers are abstract pixel art drawn from 3 colors per album, only so there's something to extract from. Use the real artwork.

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
- **Tap wave:** tapping Random, Otro or Escuchar starts a ring from the button's center. Radius grows to the screen diagonal in ~1.1s while fading out over 1.3s. Up to 4 overlapping waves. For each cell: `push += exp(−d²/6) · fade · 5`, where `d = distance(cell, origin) − radius` (in cells).
- Compose hint: `Canvas` + `withFrameNanos` loop. Precompute the 10 blended colors once per frame, not per cell.

## Accessibility

- Respect "Remove animations" (animator duration scale = 0): show theme 1 statically, with no marquee and no hue sway.
- Background and album strip are decorative (not announced by screen readers). Buttons keep their text labels; the icons are decorative.
- Text color can be briefly low-contrast during the 2.5s fades; that's expected.
