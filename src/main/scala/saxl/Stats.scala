package saxl

import scala.collection.immutable.HashMap

/**
 * Created by mglvl on 26/04/15.
 */
case class Stats(stats: List[RoundStats]) extends AnyVal {
  def numberOfRounds = stats.length
  override def toString(): String = {
    var i = 1
    stats.reverse.map { rs =>
      val s = s"Round $i:\n$rs"
      i+=1
      s
    }.mkString("\n")
  }
}


object Stats {
  def apply(): Stats = Stats(List.empty)
}

case class RoundStats(roundTimeMillis: Int, roundDataSources: HashMap[String, DataSourceRoundStats]) {
  override def toString(): String = {
    val rt = s"roundTime: $roundTimeMillis millis\n"
    val rds = roundDataSources.map{ case (name,dsrs) => s"DataSource '$name':\n$dsrs" }.mkString("\n")
    rt + rds
  }
}

case class DataSourceRoundStats(dataSourceFetches: Int, dataSourceTimeMillis: Int) {
  override def toString(): String = {
    s"Fetches: $dataSourceFetches\nData Source Time: $dataSourceTimeMillis millis"
  }
}
