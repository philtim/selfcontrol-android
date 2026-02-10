package com.t7lab.focustime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.R
import com.t7lab.focustime.ui.theme.LocalSessionColors
import kotlinx.coroutines.delay

data class Quote(val text: String, val author: String)

@Composable
fun RotatingQuoteCard(
    modifier: Modifier = Modifier
) {
    val sessionColors = LocalSessionColors.current
    val texts = stringArrayResource(R.array.motivational_quote_texts)
    val authors = stringArrayResource(R.array.motivational_quote_authors)
    require(texts.size == authors.size) { "Quote texts and authors arrays must have the same size" }

    val quotes = remember(texts, authors) {
        texts.zip(authors).map { (text, author) -> Quote(text, author) }
    }

    var currentIndex by remember { mutableIntStateOf((Math.random() * quotes.size).toInt()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(45_000)
            currentIndex = (currentIndex + 1) % quotes.size
        }
    }

    val quote = quotes[currentIndex]

    AnimatedContent(
        targetState = quote,
        transitionSpec = {
            fadeIn(animationSpec = tween(1500)) togetherWith fadeOut(animationSpec = tween(1500))
        },
        label = "quote",
        modifier = modifier.fillMaxWidth()
    ) { targetQuote ->
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "\u201C${targetQuote.text}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = sessionColors.quoteText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "\u2014 ${targetQuote.author}",
                style = MaterialTheme.typography.labelSmall,
                color = sessionColors.quoteText.copy(alpha = 0.4f),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}
