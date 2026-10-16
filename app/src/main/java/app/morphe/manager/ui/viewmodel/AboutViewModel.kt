/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.lifecycle.ViewModel
import app.morphe.manager.BuildConfig
import app.morphe.manager.util.MORPHE_WEBSITE_URL
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.RedditAlien

data class SocialLink(
    val name: String,
    val url: String,
    val preferred: Boolean = false,
)

class AboutViewModel : ViewModel() {
    companion object {
        // ORU: the website, GitHub and Reddit entries are Morphe's own accounts and are
        // left out of the oru flavour. The changelog link also points at Morphe's website and is left out.
        val socials: List<SocialLink> = buildList {
            if (!BuildConfig.ORU_UX) {
                add(
                    SocialLink(
                        name = "Website",
                        url = MORPHE_WEBSITE_URL,
                        preferred = true
                    )
                )
            }
            if (!BuildConfig.ORU_UX) {
                add(
                    SocialLink(
                        name = "Changelog",
                        url = "$MORPHE_WEBSITE_URL/changelog"
                    )
                )
            }
            if (!BuildConfig.ORU_UX) {
                add(
                    SocialLink(
                        name = "GitHub",
                        url = "https://github.com/MorpheApp"
                    )
                )
                add(
                    SocialLink(
                        name = "Reddit",
                        url = "https://reddit.com/r/MorpheApp"
                    )
                )
            }
        }

        private val socialIcons = mapOf(
            "Website" to Icons.Outlined.Public,
            "GitHub" to FontAwesomeIcons.Brands.Github,
            "Changelog" to Icons.AutoMirrored.Outlined.Article,
            "Reddit" to FontAwesomeIcons.Brands.RedditAlien,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
