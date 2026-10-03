package moe.comico.reader

fun settingsTooltip(section: String) = when(section) {
    "Account" -> "Sign in, manage your account, or sign out."
    "Profile" -> "Change your avatar, banner, display name, bio, and social links."
    "Comments" -> "See your comments and the manga or chapters they belong to."
    "Reading" -> "Choose default reader controls, layout, and sources."
    "Appearance" -> "Choose colors, date formats, and navigation labels."
    "Data" -> "Sync, import, or export your library and reading history."
    "Leaderboard" -> "See accounts ranked by reading experience."
    "Help" -> "Find reading tips and report problems."
    else -> "Learn about the app and view its source code."
}
