package com.waha.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waha.data.ThemePreferenceStore
import com.waha.ui.theme.WahaDarkBg
import com.waha.ui.theme.WahaLine
import com.waha.ui.theme.WahaTeal
import com.waha.ui.theme.WahaTextMuted
import com.waha.ui.theme.WahaTextWarm
import com.waha.BuildConfig

@Composable
fun SettingsScreen(topBarHeight: Dp, bottomBarHeight: Dp) {
    val isDarkMode by ThemePreferenceStore.isDarkMode
    var showPolicyDialog by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val websiteUrl = "https://wahatv.pages.dev/"
    val privacyPolicyUrl = "https://wahatv.pages.dev/"
    val supportEmail = "ziyad.zanfouri@gmail.com"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = topBarHeight + 20.dp, bottom = bottomBarHeight + 20.dp)
    ) {
        Text(
            text = "الإعدادات",
            color = WahaTextWarm,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("المظهر")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WahaDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = WahaTeal)
            }
            Column(modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f)) {
                Text(text = "الوضع الداكن", color = WahaTextWarm, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = "تغيير مظهر التطبيق", color = WahaTextMuted, fontSize = 13.sp)
            }
            Switch(
                checked = isDarkMode,
                onCheckedChange = { ThemePreferenceStore.setDarkMode(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = WahaTeal)
            )
        }

        HorizontalDivider(color = WahaLine, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        SettingsSectionTitle("الأمان والآباء")

        SettingsRow(
            icon = Icons.Default.Shield,
            title = "سياسة المحتوى",
            subtitle = "معايير الأمان ومراجعة الفيديوهات",
            onClick = { showPolicyDialog = true }
        )

        SettingsRow(
            icon = Icons.Default.PrivacyTip,
            title = "سياسة الخصوصية",
            subtitle = "كيف نحمي بيانات طفلك",
            onClick = { uriHandler.openUri(privacyPolicyUrl) }
        )

        HorizontalDivider(color = WahaLine, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        SettingsSectionTitle("الدعم والمعلومات")

        SettingsRow(
            icon = Icons.Default.Language,
            title = "الموقع الإلكتروني",
            subtitle = "تفضل بزيارة موقعنا الرسمي",
            onClick = { uriHandler.openUri(websiteUrl) }
        )

        SettingsRow(
            icon = Icons.Default.Email,
            title = "تواصل معنا",
            subtitle = "نستقبل استفسارات وملاحظات الآباء",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$supportEmail")
                    putExtra(Intent.EXTRA_SUBJECT, "تطبيق واحة - استفسار/ملاحظة")
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "واحة - بيئة آمنة للأطفال\nالإصدار ${BuildConfig.VERSION_NAME}",
            color = WahaTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp)
        )
    }

    if (showPolicyDialog) {
        ContentPolicyDialog(
            onDismiss = { showPolicyDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = WahaTeal,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WahaDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = WahaTeal)
        }
        Column(modifier = Modifier
            .padding(horizontal = 12.dp)
            .weight(1f)) {
            Text(text = title, color = WahaTextWarm, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = WahaTextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ContentPolicyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WahaDarkBg,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = WahaTeal
            )
        },
        title = {
            Text(
                text = "سياسة المحتوى والأمان",
                style = MaterialTheme.typography.titleLarge,
                color = WahaTextWarm
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PolicyPoint(
                    title = "مراجعة يدويّة 100%",
                    description = "لا يُنشر أي فيديو تلقائياً؛ يخضع كل محتوى للفحص والتصنيف لضمان مناسبته للأطفال."
                )
                PolicyPoint(
                    title = "خالي من الإعلانات والمحتوى الضار",
                    description = "نحرص على خلو الفيديوهات من أي مشاهد عنف، إيحاءات غير لائقة، أو إعلانات استغلالية."
                )
                PolicyPoint(
                    title = "حماية الخصوصية",
                    description = "لا نجمع أي بيانات شخصية أو معرّفات تتبع من جهاز الطفل."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("حسناً", color = WahaTeal)
            }
        }
    )
}

@Composable
private fun PolicyPoint(title: String, description: String) {
    Column {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = WahaTextWarm
        )
        Text(
            text = description,
            fontSize = 13.sp,
            color = WahaTextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}