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
 @Test fun offlineDemoStartsWithoutServer(){rule.onNodeWithText("Demo Mode").performClick();rule.onNodeWithText("오프라인 Demo Mode").assertIsDisplayed();rule.onNodeWithText("데모 3",substring=true).performClick();rule.onNodeWithText("전화 대기 중…").assertIsDisplayed()}
 @Test fun performanceSettingsExposeValidatedCallerFields(){rule.onNodeWithText("공연 설정").performClick();rule.onNodeWithText("사용자 지정 발신자 이름",substring=true).assertIsDisplayed();rule.onNodeWithText("사용자 지정 발신자 이름",substring=true).performTextInput("운명의 카드");rule.onNodeWithText("사용자 지정 문구").assertIsDisplayed();rule.onNodeWithText("BLUE_FAN").assertIsDisplayed()}
 @Test fun revealCanReachResult(){rule.onNodeWithText("새 공연 만들기").performClick();rule.onNodeWithText("AS").performClick();rule.onNodeWithText("전화 예약").performClick();rule.onNodeWithText("지금 실행").performClick();rule.onNodeWithText("수신").performClick();repeat(4){rule.onNodeWithText(if(it==3)"최종 카드 공개" else "다음 단서").performClick()};rule.waitUntil(3000){rule.onAllNodesWithText("공연 완료").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithText("공연 완료").performClick();rule.onNodeWithText("관객 반응").assertIsDisplayed()}
}
