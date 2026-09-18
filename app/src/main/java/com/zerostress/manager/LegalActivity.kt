package com.zerostress.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostress.manager.ui.ZSBackground
import com.zerostress.manager.ui.ZSCard
import com.zerostress.manager.ui.ZSTopBar
import com.zerostress.manager.ui.theme.ZeroStressTheme
import com.zerostress.manager.ui.theme.ZsPrimary
import com.zerostress.manager.ui.theme.ZsTextMuted
import com.zerostress.manager.ui.theme.ZsTextPrimary
import com.zerostress.manager.ui.theme.ZsTextSecondary

class LegalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "privacy" or "terms" - defaults to privacy
        val tab = intent.getStringExtra(EXTRA_TAB) ?: "privacy"
        setContent {
            ZeroStressTheme {
                LegalScreen(tab)
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

@Composable
private fun LegalScreen(initialTab: String) {
    val context = LocalContext.current
    val showPrivacy = initialTab != "terms"

    ZSBackground {
        Column(Modifier.fillMaxSize()) {
            ZSTopBar(
                title = if (showPrivacy) "Privacy Policy" else "Terms of Service",
                onBack = { (context as? android.app.Activity)?.finish() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Last updated: September 2026",
                    color = ZsTextMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                if (showPrivacy) PrivacyContent() else TermsContent()

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text,
        color = ZsPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .width(40.dp)
            .height(3.dp)
            .background(ZsPrimary, RoundedCornerShape(2.dp))
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        color = ZsTextSecondary,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PrivacyContent() {
    ZSCard {
        Column {
            BodyText(
                "ZERO STRESS (\"we\", \"our\") operates a team performance and " +
                    "leaderboard app for gaming squads. This policy explains what data " +
                    "we collect and how it is used."
            )

            SectionHeading("DATA WE COLLECT")
            BodyText(
                "• Account: your player name, password (stored only as a secure " +
                    "Firebase Auth credential) and the phone number or email you register with.\n" +
                "• Performance stats: kills, wins, scores, damage and other match " +
                    "numbers you or your team admin log.\n" +
                "• Chat content: messages you send in team chat and voice call chat.\n" +
                "• Device data: a Firebase Cloud Messaging token used to deliver " +
                    "push notifications, and a Firebase App Check attestation used to " +
                    "verify your install is genuine.\n" +
                "• Profile photo, if you choose to set one."
            )

            SectionHeading("HOW WE USE DATA")
            BodyText(
                "Data is used only to run the service: leaderboards, schedules, chat, " +
                    "notifications, achievements and moderation. We do not sell your " +
                    "data and we do not show advertising."
            )

            SectionHeading("CHAT & USER CONTENT")
            BodyText(
                "Team chat is visible to every member of your squad. Messages may be " +
                    "moderated: staff can remove messages, and any member can report a " +
                    "message or block another player. Reported messages are visible to " +
                    "administrators only, for moderation purposes."
            )

            SectionHeading("BLOCKING & REPORTING")
            BodyText(
                "You can block any player from chat (long-press their message → " +
                    "Block). Blocked players' messages are hidden from you everywhere " +
                    "in the app. You can also report abusive messages to moderators. " +
                    "Unblock players any time in Settings → Blocked Users."
            )

            SectionHeading("DATA STORAGE & SECURITY")
            BodyText(
                "Data is stored on Google Firebase (Firestore) with access rules that " +
                    "restrict personal data to you and your admins. Connections are " +
                    "encrypted in transit. Requests are additionally verified by " +
                    "Firebase App Check / Play Integrity."
            )

            SectionHeading("DATA RETENTION & DELETION")
            BodyText(
                "Your data is kept while your account is active. You can delete your " +
                    "account at any time in Settings → Delete Account, which removes " +
                    "your profile, stats and friends data. You may also contact the " +
                    "team admin to request deletion of specific chat content."
            )

            SectionHeading("CHILDREN")
            BodyText(
                "ZERO STRESS is not directed at children under 13. We do not " +
                    "knowingly collect data from children under 13."
            )

            SectionHeading("CONTACT")
            BodyText(
                "Questions about this policy? Contact your team administrator " +
                    "in-app via team chat."
            )
        }
    }
}

@Composable
private fun TermsContent() {
    ZSCard {
        Column {
            BodyText(
                "By creating an account or using ZERO STRESS you agree to these " +
                    "terms. If you do not agree, do not use the app."
            )

            SectionHeading("1. YOUR ACCOUNT")
            BodyText(
                "You must provide accurate registration details and keep your " +
                    "password secure. You are responsible for all activity under your " +
                    "account. Accounts are for real members of the squad; fake or " +
                    "duplicate accounts may be removed by admins."
            )

            SectionHeading("2. ACCEPTABLE USE")
            BodyText(
                "You agree NOT to:\n" +
                "• Harass, threaten, or abuse other players in chat or voice.\n" +
                "• Send spam, scams, or repeated unwanted messages.\n" +
                "• Post sexual, violent, hateful, or illegal content.\n" +
                "• Impersonate another person or staff member.\n" +
                "• Attempt to access, modify, or flood the service with automated " +
                    "tools, modified clients, or scripts.\n" +
                "• Reverse engineer the app to bypass moderation or security."
            )

            SectionHeading("3. MODERATION")
            BodyText(
                "Admins and moderators may edit, remove content, mute, demote, or " +
                    "ban accounts that violate these terms. Moderation decisions may " +
                    "be appealed to your team admin."
            )

            SectionHeading("4. USER-GENERATED CONTENT")
            BodyText(
                "You keep ownership of what you write, but grant the team the " +
                    "right to store, display and moderate it inside the app. " +
                    "Report abuse with the long-press Report action in chat; " +
                    "moderators review reports."
            )

            SectionHeading("5. SERVICE AVAILABILITY")
            BodyText(
                "The service is provided \"as is\". We aim for high availability " +
                    "but do not guarantee uninterrupted access, and we may change or " +
                    "discontinue features at any time."
            )

            SectionHeading("6. TERMINATION")
            BodyText(
                "We may suspend or delete accounts that break these terms. You may " +
                    "stop using the app and delete your account at any time from " +
                    "Settings."
            )

            SectionHeading("7. CHANGES TO THESE TERMS")
            BodyText(
                "Terms may be updated as the app evolves. Continued use after an " +
                    "update means you accept the revised terms."
            )
        }
    }
}
