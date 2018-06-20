package uk.gov.homeoffice.drt

import java.sql.{Connection, PreparedStatement}

import javax.sql.DataSource
import org.apache.commons.dbcp.BasicDataSource
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.drt.Boot.getClass

// Copied From: http://phil-rice.github.io/scala/performance/2015/10/30/Inserting-data-to-database-tables-with-scala.html
case class DataSourceDefn(url: String, userName: String, password: String,
                          classDriveName: String = "org.postgresql.Driver",
                          maxConnections: Integer = -1)

trait UsingPostgres extends HasConfig {
  implicit lazy val defn: DataSourceDefn =
    DataSourceDefn(url = config.getString("db.url"),
      userName = config.getString("db.user"),
      password = config.getString("db.password")
    )
}

trait UsingDatabase {
  implicit def defn: DataSourceDefn

  protected def createDataSource(implicit dsDefn: DataSourceDefn) = {
    val ds = new BasicDataSource()
    ds.setDriverClassName(dsDefn.classDriveName)
    ds.setUrl(dsDefn.url)
    ds.setUsername(dsDefn.userName)
    ds.setPassword(dsDefn.password)
    ds.setMaxActive(dsDefn.maxConnections)
    ds
  }

  protected def dataToDatabase(tableName: String, columnNames: List[String], data: Iterator[List[Any]])(implicit ds: DataSource) = {
    val columnsWithCommas = columnNames.mkString(",")
    val questionMarks = columnNames.map(_ => "?").mkString(",")
    val sql = s"insert into $tableName ($columnsWithCommas) values ($questionMarks)"
    for ((list, lineNo) <- data.zipWithIndex)
      withPreparedStatement(sql, { implicit statement =>
        for ((value, index) <- list.zipWithIndex)
          statement.setObject(index + 1, value)
        statement.execute
      })
  }

  protected def withDatasource[X](fn: (DataSource) => X)(implicit defn: DataSourceDefn) = {
    val ds = createDataSource
    try fn(ds) finally ds.close
  }


  protected def withConnection[X](fn: (Connection => X))(implicit ds: DataSource) = {
    val c = ds.getConnection
    try fn(c) finally c.close
  }

  protected def withPreparedStatement[X](sql: String, fn: (PreparedStatement) => X)(
    implicit ds: DataSource) = withConnection { connection =>
    val statement = connection.prepareStatement(sql)
    try fn(statement) finally statement.close
  }
}
