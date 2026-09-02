package com.cardcaller.magic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

interface MagicLabRepository {
    suspend fun createProphecy(card:PlayingCard?,expiryMinutes:Int):QrProphecy
    suspend fun setCard(prophecy:QrProphecy,card:PlayingCard):QrProphecy
    suspend fun status(prophecy:QrProphecy):QrProphecy
}

class FastApiMagicLabRepository(private val baseUrl:String=BuildConfig.CARD_CALLER_API_BASE_URL.trimEnd('/')):MagicLabRepository {
    override suspend fun createProphecy(card:PlayingCard?,expiryMinutes:Int)=withContext(Dispatchers.IO){val body=JSONObject().put("expiresInMinutes",expiryMinutes);card?.let{body.put("cardId",it.id)};parse(request("POST","/api/v1/prophecies",body.toString()))}
    override suspend fun setCard(prophecy:QrProphecy,card:PlayingCard)=withContext(Dispatchers.IO){parse(request("PUT","/api/v1/prophecies/${prophecy.roomCode}/${prophecy.token}/card",JSONObject().put("cardId",card.id).toString()))}
    override suspend fun status(prophecy:QrProphecy)=withContext(Dispatchers.IO){parse(request("GET","/api/v1/prophecies/${prophecy.roomCode}/${prophecy.token}",null))}
    private fun parse(json:JSONObject)=QrProphecy(json.getString("roomCode"),json.getString("token"),json.getString("qrUrl"),json.getString("expiresAt"),json.optString("cardId").takeIf{it.isNotBlank()},json.optBoolean("consumed"))
    private fun request(method:String,path:String,body:String?):JSONObject{val c=(URI("$baseUrl$path").toURL().openConnection()as HttpURLConnection).apply{requestMethod=method;connectTimeout=10_000;readTimeout=30_000;setRequestProperty("Accept","application/json");if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json");outputStream.use{it.write(body.toByteArray())}}};val code=c.responseCode;val text=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()};if(code !in 200..299)throw IllegalStateException(runCatching{JSONObject(text).optString("detail")}.getOrDefault("HTTP $code"));return JSONObject(text)}
}
