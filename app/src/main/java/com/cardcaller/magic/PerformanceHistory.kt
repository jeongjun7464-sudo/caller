package com.cardcaller.magic

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="performance_history")
data class PerformanceHistory(@PrimaryKey val id:String,val cardId:String,val inputMethod:String,val triggerMethod:String,val startedAt:Long,val completedAt:Long?,val durationMs:Long?,val reaction:String?,val status:String,val success:Boolean?)
@Dao interface PerformanceHistoryDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE)suspend fun save(item:PerformanceHistory)
    @Query("SELECT * FROM performance_history ORDER BY startedAt DESC")fun observeAll():Flow<List<PerformanceHistory>>
    @Query("DELETE FROM performance_history")suspend fun clear()
}
@Database(entities=[PerformanceHistory::class],version=1,exportSchema=false)
abstract class CardCallerDatabase:RoomDatabase(){abstract fun history():PerformanceHistoryDao
    companion object{@Volatile private var instance:CardCallerDatabase?=null;fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,CardCallerDatabase::class.java,"card-caller.db").build().also{instance=it}}}
}
interface PerformanceHistoryRepository{fun observe():Flow<List<PerformanceHistory>>;suspend fun save(session:PerformanceSession);suspend fun clear()}
class RoomPerformanceHistoryRepository(private val dao:PerformanceHistoryDao):PerformanceHistoryRepository{
    override fun observe()=dao.observeAll()
    override suspend fun save(session:PerformanceSession){val card=session.card?:return;dao.save(PerformanceHistory(session.id,card.id,session.inputMethod,session.triggerMethod,session.startedAt,session.completedAt,performanceDuration(session.startedAt,session.completedAt),session.reaction,session.state.name,session.success))}
    override suspend fun clear()=dao.clear()
}
object HistoryCsv {fun encode(items:List<PerformanceHistory>)=buildString{appendLine("id,cardId,inputMethod,triggerMethod,startedAt,completedAt,durationMs,reaction,status,success");items.forEach{appendLine(listOf(it.id,it.cardId,it.inputMethod,it.triggerMethod,it.startedAt,it.completedAt?:"",it.durationMs?:"",it.reaction?:"",it.status,it.success?:"").joinToString(","))}}}
data class HistorySummary(val cardUsage:Map<String,Int>,val inputSuccess:Map<String,Double>,val averagePerformanceMs:Double)
object HistoryAnalytics {
    fun between(items:List<PerformanceHistory>,from:Long,to:Long)=items.filter{it.startedAt in from..to}
    fun summarize(items:List<PerformanceHistory>):HistorySummary {
        fun rate(xs:List<PerformanceHistory>)=if(xs.isEmpty())0.0 else xs.count{it.success==true}*100.0/xs.size
        val durations=items.mapNotNull{it.durationMs}
        return HistorySummary(items.groupingBy{it.cardId}.eachCount(),items.groupBy{it.inputMethod}.mapValues{rate(it.value)},if(durations.isEmpty())0.0 else durations.average())
    }
}
