package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DESIGN TOKENS — the frontend-redesign language (build 206, Sarowar 2026-11-15).
 *
 * This supersedes the square + boxless law of builds 178/183. The new language,
 * from Sarowar's references (floating pill-nav video + editor concepts):
 *  - Dark near-black base; chrome stays black & white, with ONE accent:
 *    [AmAccentViolet] for primary actions and the tools-grid button.
 *  - FULLY ROUNDED surfaces: cards ~24 dp, sheets ~28 dp top corners,
 *    bars and buttons are full pills (capsules).
 *  - Bars FLOAT: pill-shaped bars with margins over the base, never
 *    full-width strips. Selected state = a filled lighter pill
 *    ([AmPillSelected]) holding icon + label.
 *  - Buttons have their pills back (this reverses build 183's boxless rule).
 *  - Panels/sheets live in normal layout flow — the floating look comes from
 *    margins and shape, never from overlays anchored over the canvas
 *    (the build-202 More-panel lesson).
 *  - Semantic colours kept: AmDanger (destructive), AmFxAccent (effects ruler
 *    only). User content colours (clips, effects, templates) are outside this
 *    layer. Existing token names are preserved so call sites stay compatible.
 */

// ── Base & flat surfaces ─────────────────────────────────────────────────
// Build 214 (UI ARCHITECTURE REDESIGN): warm near-black surfaces serving the
// dock + panel-stage architecture; the accent is the brand red of Sarowar's
// red/black logo (see AmAccentViolet below — the token name is kept for
// call-site compatibility, its value is now the red).
/** App base: warm near-black. */
val AmBase = Color(0xFF0B0B0D)
/** Pure black for recessed / canvas surround. */
val AmBaseDeep = Color(0xFF000000)
/** Primary panel surface — sheets, bottom bars, dialogs, the panel stage. */
val AmPanel = Color(0xFF121215)
/** Raised surface — chips, selected pills, elevated cards. */
val AmPanelHigh = Color(0xFF22222A)
/** Slightly higher for interactive hover / pressed states. */
val AmPanelElevated = Color(0xFF2A2A33)
/** Default fill for small chrome items on base. */
val AmGlassFill = Color(0xFF17171C)
/** Subtle 1px hairline (build 214: the architecture's hairline colour). */
val AmHairline = Color(0xFF26262E)
/** Stronger hairline for focused edges. */
val AmHairlineStrong = Color(0x40FFFFFF)

// ── Accents — white / grey emphasis (names kept for compatibility) ───────
/** Active / selected: pure white. */
val AmMint = Color(0xFFFFFFFF)
/** Dimmed primary. */
val AmMintDeep = Color(0xFFC8C8C8)
/** Near-white glyph accent. */
val AmCyan = Color(0xFFE8E8E8)
/** Bright white. */
val AmCyanBright = Color(0xFFFFFFFF)
/** Light grey stroke / secondary accent. */
val AmViolet = Color(0xFFCFCFCF)
/** Mid grey. */
val AmVioletDeep = Color(0xFFA8A8A8)
/** Legacy lime → white. */
val AmLime = Color(0xFFFFFFFF)
/** Neutral light grey. */
val AmOrange = Color(0xFFC8C8C8)
/** White. */
val AmPink = Color(0xFFFFFFFF)
/** Soft red — only for destructive actions. */
val AmDanger = Color(0xFFFF5C5C)
/**
 * Brand red — reserved exclusively for the Effects parameter ruler thumb
 * (build 214: the needle joins the architecture's accent; the token stays
 * effects-only). Do not use elsewhere in chrome.
 */
val AmFxAccent = Color(0xFFFF3B47)
/** Ink drawn on white fills (Export label, badges). */
val AmInk = Color(0xFF0A0A0A)

// ── Build 206: the redesign's surfaces + accent ──────────────────────────
/**
 * The one accent (build 214: BRAND RED #FF3B47 — Sarowar's red/black logo;
 * it marks selection, primary actions, slider fills and the dock's Add).
 * The token keeps its build-206 name so call sites stay compatible.
 */
val AmAccentViolet = Color(0xFFFF3B47)
/** Alias for new code (build 214): the same brand red, honestly named. */
val AmAccent = AmAccentViolet
/** Floating pill bars (transport, toolbars, header): near-black, a step above the base. */
val AmBarSurface = Color(0xFF121215)
/** The filled pill of a selected / active item inside a bar or grid (build 214: dark red tint). */
val AmPillSelected = Color(0xFF2A1518)
/** Card surface on the base (project cards, panel-stage panels). */
val AmCardSurface = Color(0xFF17171C)

/** Selected-layer header slate (build 211): the Alight reference's dark blue-grey bar, distinct from the editor black. */
val AmSlateHeader = Color(0xFF262635)

// ── Text hierarchy ───────────────────────────────────────────────────────
/** Secondary text. */
val AmTextDim = Color(0xFFA7A7B3)
/** Tertiary / disabled text. */
val AmTextFaint = Color(0xFF6E6E78)
/** Primary text (explicit token for new code). */
val AmTextPrimary = Color(0xFFF5F5F7)

// ── Slider / ruler ──────────────────────────────────────────────────────
val AmTickMinor = Color(0xFF383838)
val AmTickMajor = Color(0xFF787878)

// ── Brush factories (still flat SolidColor for compatibility) ────────────
fun amMintCyanBrush(): Brush = SolidColor(Color.White)
fun amGlassBrush(): Brush = SolidColor(AmPanel)
fun amGlassChipBrush(): Brush = SolidColor(AmPanelHigh)
fun amCyanVioletBrush(): Brush = SolidColor(Color.White)
/** The floating-bar fill (build 206). */
fun amBarBrush(): Brush = SolidColor(AmBarSurface)

// ── Corner scale — fully rounded (build 206 redesign) ────────────────────
// Bars and buttons are full pills (CircleShape at the call site); these
// tokens size the rounded surfaces. Functional round shapes (play disc,
// thumbs, etc.) stay circular and are not affected by these tokens.
/** Large cards / dialogs (build 214: cards are 20 dp). */
val AmCornerCard = 20.dp
/** Bottom sheets / the panel stage: top corners (build 214: 24 dp). */
val AmCornerSheet = 24.dp
/** Panels, major containers. */
val AmCornerPanel = 20.dp
/** Toolbar items, medium chips, buttons. */
val AmCornerItem = 8.dp
/** Small chips, readout pills. */
val AmCornerChip = 6.dp
/** Micro badges / tiny containers. */
val AmCornerMicro = 4.dp
/**
 * The bottom-sheet shape (build 207): [AmCornerSheet] top corners, square
 * bottom (the sheet sits on the screen edge). Every ModalBottomSheet in the
 * UI layer uses this one shape.
 */
val AmSheetShape = RoundedCornerShape(topStart = AmCornerSheet, topEnd = AmCornerSheet)

// ── Spacing scale ────────────────────────────────────────────────────────
object AmSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

// ── Elevation / stroke helpers ───────────────────────────────────────────
/** Standard content padding inside panels. */
val AmPanelPadding = 16.dp
/** Compact content padding (toolbars, dense lists). */
val AmPanelPaddingCompact = 12.dp
/** Minimum comfortable touch target. */
val AmTouchTarget = 44.dp

// ── Type emphasis ────────────────────────────────────────────────────────
/** Tiny uppercase micro-label — call site should apply .uppercase(). */
val AmMicroLabel = TextStyle(
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp,
    color = AmTextDim
)
/** Large bold numeral for stats / counters. */
val AmBigNumeral = TextStyle(
    fontSize = 28.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.5).sp
)
/** Control / toolbar label. */
val AmControlLabel = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp
)
/** Section title inside sheets. */
val AmSectionTitle = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.1.sp
)
/** Body text for descriptions. */
val AmBody = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.15.sp,
    lineHeight = 20.sp
)
