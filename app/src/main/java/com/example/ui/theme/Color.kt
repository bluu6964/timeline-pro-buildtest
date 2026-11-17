package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Professional dark palette — aligned with DesignTokens (2026-11-15 polish).
// Chrome stays monochrome CapCut-style. Clip-type and content colours remain
// slightly tinted so timeline layers stay scannable.

val EditorBackground = AmBase
val EditorSurface = AmPanelHigh
// Timeline lane controls (left pills)
val LanePill = Color(0xFFF3F3F5)
val LanePillBorder = Color(0x0F000000)
val EditorSurfaceVariant = AmPanelElevated
val EditorDivider = Color(0x0F000000)
val GlassSurface = AmPanelHigh
val GlassHighlight = Color(0xFFFFFFFF)
// Canvas / project background: full black.
val CanvasBackground = AmBaseDeep
val AccentBlue = Color(0xFF18181B)
val AccentBlueSoft = Color(0xFF3F3F46)

// Timeline & Track colors
val TrackBackground = Color(0xFFFFFFFF)
val TrackBorder = Color(0x0F000000)
val PlayheadColor = Color(0xFF18181B) // build 216: the playhead line is ink; the lime lives in the ruler cap
val PlayheadAccent = Color(0xFFFFFFFF)
val RulerTickColor = Color(0xFFB8B8BE)
val RulerSubTickColor = Color(0xFFD0D0D4)

// Clip type colors — muted, professional surfaces (content, not chrome)
val ClipVideoColor = Color(0xFF243A52)
val ClipAudioColor = Color(0xFF1E3A2C)
val ClipShapeColor = Color(0xFF1A3340)
val ClipOverlayColor = Color(0xFF3A253C)
val ClipTextColor = Color(0xFF3A8FCB)
val ClipStickerColor = Color(0xFF3A2C20)

// Legacy names kept for source compatibility. "Neon" family → white/grey.
val NeonCyan = AccentBlue
val NeonGreen = Color(0xFF18181B)
val ElectricBlue = AccentBlue
val AccentAmber = Color(0xFFFF9F0A)
val SelectedClipBorder = Color(0xFF18181B)
val TrimHandleColor = AccentBlue

// Text & Icons
val TextPrimary = Color(0xFF18181B)
val TextSecondary = Color(0xFF76767A)
val TextTertiary = Color(0xFFAEAEB4)
val IconActive = Color(0xFF18181B)
val IconDisabled = Color(0xFFC4C4C9)

// Text/icon colour used on top of white fills
val OnAccent = AmInk

// ---- Former neon / gold look — chrome roles now white/grey ----
val NeonLime = Color(0xFFC8F91F)
val NeonLimeDeep = Color(0xFF9DC90E)
val NeonLimeLight = Color(0xFFE9F9B0)
val GoldClip = Color(0xFFF7D060)
val GoldClipDeep = Color(0xFFD9A233)
val GoldThumbTop = Color(0xFFD99A1C)
val GoldThumbBottom = Color(0xFF8C5A06)
val PillTop = Color(0xFFF3F3F4)
val PillBottom = Color(0xFFE8E8EB)
val TrimHandleTop = Color(0xFFFFFFFF)
val TrimHandleBottom = Color(0xFFF3F3F4)
val TimeDim = Color(0xFF76767A)

// ---- Timeline reference ----
val TimelineBlack = AmBaseDeep
val RulerOrange = Color(0xFF18181B)      // elapsed (ink, build 216)
val RulerGrey = Color(0xFFD0D0D4)        // upcoming
val RulerTimePill = Color(0xFFFFFFFF)
val RulerTimeText = Color(0xFF18181B)
val LanePillDark = Color(0xFFF3F3F5)
val LaneEyeTint = Color(0xFF18181B)
val LaneThumbWhite = Color(0xFFDEDEDE)
val ShapeBarTop = Color(0xFFA0E24C)
val ShapeBarMid = Color(0xFF80CE22)
val ShapeBarBottom = Color(0xFF69BD07)

// ---- Build 215: neon timeline bars (Sarowar's reference, neon-bars.html) ----
// Solid vivid bar bodies, every one carrying the same white crosshatch texture.
val NeonBarPink = Color(0xFFF444B0)    // TEXT (build 216 reference pink)
val NeonBarBlue = Color(0xFF0F70F5)    // VIDEO
val NeonBarViolet = Color(0xFF8014E0)  // SHAPE
val NeonBarCyan = Color(0xFF0B84FE)    // IMAGE (a VIDEO clip with isImage) (build 216 reference blue)
val NeonBarOrange = Color(0xFFFF940F)  // STICKER / OVERLAY
val NeonBarGreen = Color(0xFF14D430)   // AUDIO
val NeonBarIndigo = Color(0xFF3A1AE8)  // GROUP / adjustment
val NeonBarGold = Color(0xFFF7B500)    // SHAPE (build 216 reference gold)
/** Build 216: a video clip's filmstrip body (dark) under its frame thumbs. */
val ClipFilmBody = Color(0xFF2B1818)
/** Build 216: the reference lime accent. */
val LimeAccent = Color(0xFFC8F91F)
/** Build 216: light-lime active wash for toggles. */
val LimeWash = Color(0xFFE9F9B0)
/** Build 216: named-marker orange on the ruler. */
val MarkerOrange = Color(0xFFFF7A1A)
val FabCyan = Color(0xFFC8F91F)
val FabBlueDeep = Color(0xFF2E2E2E)
val FabBlueMid = Color(0xFF5A5A5A)
val FabBlueLight = Color(0xFFC0C0C0)

// ---- Header ----
val HeaderBarFill = Color(0xFFFFFFFF)
val HeaderBarBorder = Color(0x0F000000)
val HeaderButtonFill = Color(0xFFF3F3F4)
val HeaderPillFill = Color(0xFFF3F3F4)
val HeaderCyan = Color(0xFF18181B)
val HeaderText = Color(0xFF18181B)
