package com.cardcaller.magic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

interface InstagramPublishRepository {
    suspend fun account():InstagramAccount
    suspend fun publish(card:PlayingCard,type:InstagramPublishType,requestId:String):InstagramPublishResult
}

class FastApiInstagramRepository(private val baseUrl:String=BuildConfig.CARD_CALLER_API_BASE_URL.trimEnd('/')):InstagramPublishRepository {
    override suspend fun account()=withContext(Dispatchers.IO){
        val json=request("GET","/api/v1/account",null,null)
        InstagramAccount(json.getString("username"),json.getString("profileUrl"))
    }
    override suspend fun publish(card:PlayingCard,type:InstagramPublishType,requestId:String)=withContext(Dispatchers.IO){
        val body=JSONObject().put("cardId",card.id).put("publishType",type.name).toString()
        val json=request("POST","/api/v1/publish",body,requestId)
        InstagramPublishResult(json.getString("requestId"),InstagramPublishStatus.valueOf(json.getString("status")),json.optString("mediaId").takeIf{it.isNotBlank()},json.optString("permalink").takeIf{it.isNotBlank()},json.optString("createdAt").takeIf{it.isNotBlank()},json.optString("error").takeIf{it.isNotBlank()})
    }
    private fun request(method:String,path:String,body:String?,requestId:String?):JSONObject {
        val connection=(URI("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=10_000;readTimeout=60_000;setRequestProperty("Accept","application/json");requestId?.let{setRequestProperty("Idempotency-Key",it)};if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json");outputStream.use{o->o.write(body.toByteArray())}}}
        val code=connection.responseCode;val text=(if(code in 200..299)connection.inputStream else connection.errorStream).bufferedReader().use{it.readText()}
        if(code !in 200..299)throw IllegalStateException(runCatching{JSONObject(text).optString("detail")}.getOrDefault("HTTP $code"))
        return JSONObject(text)
    }
}
