package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Professional dark palette — aligned with DesignTokens (2026-11-15 polish).
// Chrome stays monochrome CapCut-style. Clip-type and content colours remain
// slightly tinted so timeline layers stay scannable.

val EditorBackground = AmBase
val EditorSurface = AmPanelHigh
// Timeline lane controls (left pills)
val LanePill = Color(0xFF1A1A1A)
val LanePillBorder = Color(0xFF2E2E2E)
val EditorSurfaceVariant = AmPanelElevated
val EditorDivider = Color(0xFF3A3A3A)
val GlassSurface = AmPanelHigh
val GlassHighlight = Color(0xFFFFFFFF)
// Canvas / project background: full black.
val CanvasBackground = AmBaseDeep
val AccentBlue = Color(0xFFFFFFFF)
val AccentBlueSoft = Color(0xFFD0D0D0)

// Timeline & Track colors
val TrackBackground = Color(0xFF161616)
val TrackBorder = Color(0xFF2E2E2E)
val PlayheadColor = Color(0xFFF5F5F7)
val PlayheadAccent = Color(0xFFFFFFFF)
val RulerTickColor = Color(0xFFB8B8B8)
val RulerSubTickColor = Color(0xFF555555)

// Clip type colors — muted, professional surfaces (content, not chrome)
val ClipVideoColor = Color(0xFF243A52)
val ClipAudioColor = Color(0xFF1E3A2C)
val ClipShapeColor = Color(0xFF1A3340)
val ClipOverlayColor = Color(0xFF3A253C)
val ClipTextColor = Color(0xFF3A8FCB)
val ClipStickerColor = Color(0xFF3A2C20)

// Legacy names kept for source compatibility. "Neon" family → white/grey.
val NeonCyan = AccentBlue
val NeonGreen = Color(0xFFFFFFFF)
val ElectricBlue = AccentBlue
val AccentAmber = Color(0xFFFF9F0A)
val SelectedClipBorder = Color(0xFFF2F2F7)
val TrimHandleColor = AccentBlue

// Text & Icons
val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFFA1A1A6)
val TextTertiary = Color(0xFF6E6E73)
val IconActive = Color(0xFFE5E5EA)
val IconDisabled = Color(0xFF555555)

// Text/icon colour used on top of white fills
val OnAccent = AmInk

// ---- Former neon / gold look — chrome roles now white/grey ----
val NeonLime = Color(0xFFFFFFFF)
val NeonLimeDeep = Color(0xFF8A8A8A)
val NeonLimeLight = Color(0xFFE8E8E8)
val GoldClip = Color(0xFFF7D060)
val GoldClipDeep = Color(0xFFD9A233)
val GoldThumbTop = Color(0xFFD99A1C)
val GoldThumbBottom = Color(0xFF8C5A06)
val PillTop = Color(0xFF262626)
val PillBottom = Color(0xFF161616)
val TrimHandleTop = Color(0xFF3A3A3A)
val TrimHandleBottom = Color(0xFF2A2A2A)
val TimeDim = Color(0xFF8A8A8A)

// ---- Timeline reference ----
val TimelineBlack = AmBaseDeep
val RulerOrange = Color(0xFFF5F5F5)      // elapsed (white)
val RulerGrey = Color(0xFF555555)        // upcoming
val RulerTimePill = Color(0xFF121212)
val RulerTimeText = Color(0xFFEDEDED)
val LanePillDark = Color(0xFF0C0C0C)
val LaneEyeTint = Color(0xFFC8C8C8)
val LaneThumbWhite = Color(0xFFDEDEDE)
val ShapeBarTop = Color(0xFFA0E24C)
val ShapeBarMid = Color(0xFF80CE22)
val ShapeBarBottom = Color(0xFF69BD07)
val FabCyan = Color(0xFFFFFFFF)
val FabBlueDeep = Color(0xFF2E2E2E)
val FabBlueMid = Color(0xFF5A5A5A)
val FabBlueLight = Color(0xFFC0C0C0)

// ---- Header ----
val HeaderBarFill = Color(0xFF0C0C0C)
val HeaderBarBorder = Color(0xFF242424)
val HeaderButtonFill = Color(0xFF1A1A1A)
val HeaderPillFill = Color(0xFF1F1F1F)
val HeaderCyan = Color(0xFFFFFFFF)
val HeaderText = Color(0xFFEEF2F7)
