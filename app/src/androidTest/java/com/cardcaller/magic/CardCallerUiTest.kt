package com.cardcaller.magic
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class CardCallerUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun homeShowsCoreActions(){rule.onNodeWithText("카드 직접 선택").assertIsDisplayed();rule.onNodeWithText("비밀 제스처 입력").assertIsDisplayed();rule.onNodeWithText("원격 카드 전송").assertIsDisplayed()}
 @Test fun pickerSelectsAndSchedules(){rule.onNodeWithText("카드 직접 선택").performClick();rule.onAllNodesWithContentDescription("선택됨").assertCountEquals(0);rule.onAllNodesWithText("♠\nA")[0].performClick();rule.onNodeWithText("전화 예약").assertIsDisplayed()}
 @Test fun magicLabShowsAllMagicFeatures(){rule.onNodeWithText("Magic Lab").performClick();rule.onNodeWithText("QR 카드 예언").assertIsDisplayed();rule.onNodeWithText("알림 카드 예언").assertIsDisplayed();rule.onNodeWithText("거짓말 탐지기 마술").assertIsDisplayed();rule.onNodeWithText("사진 속 카드 출현").assertIsDisplayed();rule.onNodeWithText("다중 관객 모드").assertIsDisplayed()}
}
