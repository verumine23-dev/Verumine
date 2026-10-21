package com.monchamp.verusfarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sélecteur de langue plein écran (premier lancement + réglages). */
@Composable
fun LanguagePicker(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(if (I18n.lang.isNotBlank()) I18n.lang else "fr") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0728), Color(0xFF07021A))))
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(28.dp))
            Text(I18n.t("lang_title"), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFF5F3FF))
            Spacer(Modifier.height(6.dp))
            Text(I18n.t("lang_subtitle"), fontSize = 14.sp, color = Color(0xFFA79BD0))
            Spacer(Modifier.height(22.dp))

            I18n.languages.forEach { lg ->
                val on = sel == lg.code
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color(0x338B5CF6) else Color(0x22130B33))
                        .border(
                            1.dp,
                            if (on) Color(0xFFA855F7) else Color(0x338B5CF6),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { sel = lg.code; I18n.set(ctx, lg.code) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lg.flag, fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(lg.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = if (on) Color(0xFFFFFFFF) else Color(0xFFEAE6FF))
                    Spacer(Modifier.weight(1f))
                    if (on) Text("✓", fontSize = 20.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { I18n.set(ctx, sel); onDone() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(I18n.t("continue"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.height(20.dp))
        }
    }
}
