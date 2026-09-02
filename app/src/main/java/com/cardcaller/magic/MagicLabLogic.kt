package com.cardcaller.magic

object MagicLabLogic {
    fun notificationContent(template:String,card:PlayingCard)=template.replace("%CARD%",card.display(CardDisplayFormat.KOREAN))
    fun ttsPhrase(card:PlayingCard,language:TtsLanguage)=if(language==TtsLanguage.KOREAN)"당신이 선택한 카드는 ${card.display(CardDisplayFormat.KOREAN)}입니다." else "Your selected card is ${card.display(CardDisplayFormat.ENGLISH)}."
    fun forcedLieDetectorResult(preselected:PlayingCard,answers:List<Boolean>)=preselected
    fun sanitizeTransform(value:OverlayTransform)=value.copy(x=value.x.coerceIn(0f,1f),y=value.y.coerceIn(0f,1f),scale=value.scale.coerceIn(.1f,1.5f),rotation=value.rotation.coerceIn(-180f,180f),alpha=value.alpha.coerceIn(0f,1f),shadow=value.shadow.coerceAtLeast(0f))
}
