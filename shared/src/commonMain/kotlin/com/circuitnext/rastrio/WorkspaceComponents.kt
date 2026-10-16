/**
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.circuitnext.rastrio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import rastrio.shared.generated.resources.*

@Composable
internal fun WorkspaceIcon(icon: DrawableResource, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(24.dp))
}

@Composable
internal fun WorkspaceHeader(title: String, backLabel: String, onBack: () -> Unit, enabled: Boolean = true, compact: Boolean = false) {
    val back: @Composable () -> Unit = {
        TextButton(onClick = onBack, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).authoringFocus().semantics { contentDescription = backLabel },
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            WorkspaceIcon(Res.drawable.ic_arrow_back)
            Spacer(Modifier.width(8.dp))
            Text(backLabel)
        }
    }
    if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        back()
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        back()
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun WorkspacePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun WorkspacePrimaryButton(label: String, enabled: Boolean = true, modifier: Modifier = Modifier,
    icon: DrawableResource? = null, onClick: () -> Unit) {
    val roles = LocalAuthoringColors.current
    Button(onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
        modifier = modifier.heightIn(min = 52.dp).authoringFocus(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(disabledContainerColor = roles.disabledSurface,
            disabledContentColor = roles.disabledText)) {
        if (icon != null) { WorkspaceIcon(icon); Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
}

@Composable
internal fun WorkspaceOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val roles = LocalAuthoringColors.current
    OutlinedButton(onClick, modifier = modifier.heightIn(min = 48.dp).authoringFocus(), enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = roles.disabledText),
        content = content)
}

/** A decorative document thumbnail, never a representation of prepared physical output. */
@Composable
internal fun DocumentThumbnail() {
    Box(Modifier.size(72.dp, 92.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Surface(Modifier.size(64.dp, 82.dp).rotate(3f), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(3.dp)) {}
        Surface(Modifier.size(64.dp, 82.dp).rotate(-5f), shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(3.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkspaceIcon(Res.drawable.ic_description, Modifier.size(20.dp))
                repeat(4) { index ->
                    Surface(Modifier.fillMaxWidth(if (index == 3) 0.65f else 1f).height(2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant) {}
                }
            }
        }
    }
}

@Composable
internal fun WorkspaceDestination(title: String, description: String, icon: DrawableResource,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier.authoringFocus(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.background),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                WorkspaceIcon(icon, Modifier.padding(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
