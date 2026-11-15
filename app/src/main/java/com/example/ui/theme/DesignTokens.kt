package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DESIGN TOKENS — Professional CapCut-inspired black & white language (2026-11-15 polish).
 *
 * Goals of this pass:
 *  - Keep the strict monochrome chrome (no neon, no glass, no gradients in UI chrome).
 *  - Add a small, consistent radius scale so panels/chips feel modern instead of harsh-square.
 *  - Introduce a clear spacing scale and refined surface hierarchy for better visual rhythm.
 *  - Improve type hierarchy and contrast for readability.
 *  - Preserve every existing token name so call-sites stay source-compatible.
 *
 * Language rules (unchanged spirit):
 *  - Backgrounds: near-black base, flat dark greys for panels.
 *  - Emphasis: white vs grey only. Primary actions = solid white + black text.
 *  - One semantic colour kept: AmDanger (destructive). AmFxAccent stays limited to effects ruler.
 *  - User content colours (clips, effects, templates) are outside this layer.
 */

// ── Base & flat surfaces ─────────────────────────────────────────────────
/** App base: near-black, neutral. */
val AmBase = Color(0xFF0A0A0A)
/** Pure black for recessed / canvas surround. */
val AmBaseDeep = Color(0xFF000000)
/** Primary panel surface — sheets, bottom bars, dialogs. */
val AmPanel = Color(0xFF141414)
/** Raised surface — chips, selected pills, elevated cards. */
val AmPanelHigh = Color(0xFF1F1F1F)
/** Slightly higher for interactive hover / pressed states. */
val AmPanelElevated = Color(0xFF2A2A2A)
/** Default fill for small chrome items on base. */
val AmGlassFill = Color(0xFF1A1A1A)
/** Subtle 1px hairline. */
val AmHairline = Color(0x22FFFFFF)
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
 * Magenta-pink — reserved exclusively for the Effects parameter ruler thumb.
 * Do not use elsewhere in chrome.
 */
val AmFxAccent = Color(0xFFFF2E7E)
/** Ink drawn on white fills (Export label, badges). */
val AmInk = Color(0xFF0A0A0A)

// ── Text hierarchy ───────────────────────────────────────────────────────
/** Secondary text. */
val AmTextDim = Color(0xFFA3A3A3)
/** Tertiary / disabled text. */
val AmTextFaint = Color(0xFF6B6B6B)
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

// ── Corner scale — soft professional radii (CapCut-like) ─────────────────
// Previous strict 0.dp square look is relaxed for better modern feel while
// remaining simple and flat. Functional round shapes (play disc, thumbs, etc.)
// stay circular and are not affected by these tokens.
/** Large cards / dialogs / bottom sheets. */
val AmCornerCard = 12.dp
/** Panels, major containers. */
val AmCornerPanel = 10.dp
/** Toolbar items, medium chips, buttons. */
val AmCornerItem = 8.dp
/** Small chips, readout pills. */
val AmCornerChip = 6.dp
/** Micro badges / tiny containers. */
val AmCornerMicro = 4.dp

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
