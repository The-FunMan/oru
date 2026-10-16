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
        // Oru does not list any social links.
        val socials: List<SocialLink> = emptyList()

        private val socialIcons = mapOf(
            "Website" to Icons.Outlined.Public,
            "GitHub" to FontAwesomeIcons.Brands.Github,
            "Changelog" to Icons.AutoMirrored.Outlined.Article,
            "Reddit" to FontAwesomeIcons.Brands.RedditAlien,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
