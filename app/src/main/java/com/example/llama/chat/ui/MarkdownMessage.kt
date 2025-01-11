package com.example.llama.chat.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.GrammarLocator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb

private class SimpleGrammarLocator : GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? = null
    override fun languages(): Set<String> = setOf("java", "kotlin", "python", "javascript", "json")
}

@Composable
fun MarkdownMessage(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val isDarkTheme = MaterialTheme.colorScheme.surface.red < 0.5f
    
    // 创建并记住Markwon实例
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(
                Prism4j(SimpleGrammarLocator()),
                Prism4jThemeDarkula.create()
            ))
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                // 设置文本视图的基本属性
                setTextIsSelectable(true)
                setTextColor(textColor)
                textSize = 16f
                // 设置链接颜色和点击支持
                setLinkTextColor(linkColor)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
} 