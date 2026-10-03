package moe.comico.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

data class BadgeArtwork(val name: String, val url: String = "", val drawable: Int? = null, val glyph: String? = null)

// Catalog and special artwork match Comico's UserIconGlyph component.
val badgeCatalog: Map<String, BadgeArtwork> = buildMap {
    put("supporter", BadgeArtwork("Supporter", drawable = R.drawable.badge_supporter))
    put("early-adopter", BadgeArtwork("Early Bud", drawable = R.drawable.badge_early))
    put("discord-linked", BadgeArtwork("Welcome to the game", drawable = R.drawable.badge_discord))
    put("staff", BadgeArtwork("Staff", glyph = "🛠️"))
    put("translator", BadgeArtwork("Translator", glyph = "🌐"))
    put("contributor", BadgeArtwork("Contributor", glyph = "⭐"))
    listOf(
        "first-page" to "First Page",
        "bookworm-bronze" to "Bookworm I", "bookworm-silver" to "Bookworm II", "bookworm-gold" to "Bookworm III",
        "night-owl" to "Night Owl", "binge-reader" to "Binge Reader", "completionist" to "Completionist",
        "explorer" to "Explorer", "polyglot" to "Polyglot", "curator" to "Curator", "hoarder" to "Hoarder",
        "critic" to "Critic", "first-words" to "First Words", "conversationalist" to "Conversationalist",
        "crowd-favorite" to "Crowd Favorite", "cheerleader" to "Cheerleader", "reactor" to "Reactor",
        "peacekeeper" to "Peacekeeper", "first-upload" to "First Upload",
        "scanlator-bronze" to "Scanlator I", "scanlator-silver" to "Scanlator II", "scanlator-gold" to "Scanlator III",
        "group-founder" to "Group Founder", "speedrunner" to "Speedrunner",
        "veteran-silver" to "Veteran I", "veteran-gold" to "Veteran II", "patron" to "Patron",
        "developer" to "Developer", "bug-hunter" to "Bug Hunter", "launch-night" to "Launch Night",
        "halloween" to "Halloween", "lunar-new-year" to "Lunar New Year"
    ).forEach { (id, name) -> put(id, BadgeArtwork(name, "$BASE_URL/badges/$id-50.png")) }
}

fun awardedBadgeArtwork(ids: List<String>, supporter: Boolean): List<BadgeArtwork> =
    (ids + if(supporter) listOf("supporter") else emptyList()).distinct().map { id ->
        badgeCatalog[id] ?: BadgeArtwork(id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
            "$BASE_URL/badges/$id-50.png")
    }

@Composable
fun BadgeImage(badge: BadgeArtwork) {
    // Keep equal layout slots while compensating for the Discord logo's wider silhouette.
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        val modifier = Modifier.size(if (badge.drawable == R.drawable.badge_discord) 24.dp else 28.dp)
        when {
            badge.drawable != null -> Image(
                painterResource(badge.drawable), badge.name, modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            badge.glyph != null -> Text(
                badge.glyph, Modifier.semantics { contentDescription = badge.name }, fontSize = 24.sp
            )
            else -> SubcomposeAsyncImage(
                model = badge.url, contentDescription = badge.name, modifier = modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                error = { Text("?", Modifier.semantics { contentDescription = "${badge.name}: image unavailable" }) })
        }
    }
}
