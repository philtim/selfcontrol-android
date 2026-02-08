package com.t7lab.focustime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class Quote(val text: String, val author: String)

val motivationalQuotes = listOf(
    Quote("The secret of getting ahead is getting started.", "Mark Twain"),
    Quote("Focus on being productive instead of busy.", "Tim Ferriss"),
    Quote("It is during our darkest moments that we must focus to see the light.", "Aristotle"),
    Quote("Concentrate all your thoughts upon the work at hand.", "Alexander Graham Bell"),
    Quote("The successful warrior is the average man, with laser-like focus.", "Bruce Lee"),
    Quote("Where focus goes, energy flows.", "Tony Robbins"),
    Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
    Quote("The only way to do great work is to love what you do.", "Steve Jobs"),
    Quote("You don't have to be great to start, but you have to start to be great.", "Zig Ziglar"),
    Quote("Starve your distractions, feed your focus.", "Daniel Goleman"),
    Quote("Almost everything will work again if you unplug it for a few minutes, including you.", "Anne Lamott"),
    Quote("Your future is created by what you do today, not tomorrow.", "Robert Kiyosaki"),
    Quote("Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
    Quote("Deep work is the ability to focus without distraction on a cognitively demanding task.", "Cal Newport"),
    Quote("You will never reach your destination if you stop and throw stones at every dog that barks.", "Winston Churchill"),
)

@Composable
fun RotatingQuoteCard(
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableIntStateOf((Math.random() * motivationalQuotes.size).toInt()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            currentIndex = (currentIndex + 1) % motivationalQuotes.size
        }
    }

    val quote = motivationalQuotes[currentIndex]

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        AnimatedContent(
            targetState = quote,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "quote"
        ) { targetQuote ->
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "\u201C${targetQuote.text}\u201D",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "\u2014 ${targetQuote.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
