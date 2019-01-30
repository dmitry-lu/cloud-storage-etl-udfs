package com.exasol.cloudetl.util

import com.exasol.cloudetl.data.ExaColumnInfo

import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.OriginalType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Type.Repetition
import org.apache.parquet.schema.Types

object SchemaUtil {

  // Maps the precision value into the number of bytes
  // Adapted from:
  //  - org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe.java
  val PRECISION_TO_BYTE_SIZE: Seq[Int] = {
    for {
      prec <- 1 to 38 // [1 .. 38]
      power = Math.pow(10, prec.toDouble) // scalastyle:ignore magic.number
      size = Math.ceil((Math.log(power - 1) / Math.log(2) + 1) / 8)
    } yield size.toInt
  }

  /**
   * Given the Exasol column information returns Parquet [[org.apache.parquet.schema.MessageType]]
   */
  def createParquetMessageType(columns: Seq[ExaColumnInfo], schemaName: String): MessageType = {
    val types = columns.map(exaColumnToParquetType(_))
    new MessageType(schemaName, types: _*)
  }

  /**
   * Given Exasol column [[com.exasol.cloudetl.data.ExaColumnInfo]] information convert it into
   * Parquet [[org.apache.parquet.schema.Type$]]
   */
  def exaColumnToParquetType(colInfo: ExaColumnInfo): Type = {
    val colName = colInfo.name
    val colType = colInfo.`type`
    val repetition = if (colInfo.isNullable) Repetition.OPTIONAL else Repetition.REQUIRED

    // In below several lines, I try to pattern match on Class[X] of Java types.
    // Please also read:
    // https://stackoverflow.com/questions/7519140/pattern-matching-on-class-type
    object JTypes {
      val jInteger: Class[java.lang.Integer] = classOf[java.lang.Integer]
      val jLong: Class[java.lang.Long] = classOf[java.lang.Long]
      val jBigDecimal: Class[java.math.BigDecimal] = classOf[java.math.BigDecimal]
      val jDouble: Class[java.lang.Double] = classOf[java.lang.Double]
      val jBoolean: Class[java.lang.Boolean] = classOf[java.lang.Boolean]
      val jString: Class[java.lang.String] = classOf[java.lang.String]
      val jSqlDate: Class[java.sql.Date] = classOf[java.sql.Date]
      val jSqlTimestamp: Class[java.sql.Timestamp] = classOf[java.sql.Timestamp]
    }
    import JTypes._

    colType match {
      case `jInteger` =>
        if (colInfo.precision > 0) {
          Types
            .primitive(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY, repetition)
            .precision(colInfo.precision)
            .scale(colInfo.scale)
            .length(PRECISION_TO_BYTE_SIZE(colInfo.precision - 1))
            .as(OriginalType.DECIMAL)
            .named(colName)
        } else {
          Types
            .primitive(PrimitiveTypeName.INT32, repetition)
            .named(colName)
        }

      case `jLong` =>
        if (colInfo.precision > 0) {
          Types
            .primitive(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY, repetition)
            .precision(colInfo.precision)
            .scale(colInfo.scale)
            .length(PRECISION_TO_BYTE_SIZE(colInfo.precision - 1))
            .as(OriginalType.DECIMAL)
            .named(colName)
        } else {
          Types
            .primitive(PrimitiveTypeName.INT64, repetition)
            .named(colName)
        }

      case `jBigDecimal` =>
        Types
          .primitive(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY, repetition)
          .precision(colInfo.precision)
          .scale(colInfo.scale)
          .length(PRECISION_TO_BYTE_SIZE(colInfo.precision - 1))
          .as(OriginalType.DECIMAL)
          .named(colName)

      case `jDouble` =>
        Types
          .primitive(PrimitiveTypeName.DOUBLE, repetition)
          .named(colName)

      case `jString` =>
        if (colInfo.length > 0) {
          Types
            .primitive(PrimitiveTypeName.BINARY, repetition)
            .as(OriginalType.UTF8)
            .length(colInfo.length)
            .named(colName)
        } else {
          Types
            .primitive(PrimitiveTypeName.BINARY, repetition)
            .as(OriginalType.UTF8)
            .named(colName)
        }

      case `jBoolean` =>
        Types
          .primitive(PrimitiveTypeName.BOOLEAN, repetition)
          .named(colName)

      case `jSqlDate` =>
        Types
          .primitive(PrimitiveTypeName.INT32, repetition)
          .as(OriginalType.DATE)
          .named(colName)

      case `jSqlTimestamp` =>
        Types
          .primitive(PrimitiveTypeName.INT96, repetition)
          .named(colName)

      case _ =>
        throw new RuntimeException(s"Cannot convert Exasol type '$colType' to Parquet type.")
    }
  }

}
