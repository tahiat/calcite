/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.rel.rel2sql;

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelProtoDataType;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.SchemaVersion;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Table;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

/**
 * Tests for {@link RelToSqlConverter} on a schema that has nested structures of multiple
 * levels.
 */
class RelToSqlConverterStructsTest {

  private static final Schema SCHEMA = new Schema() {
    @Override public Table getTable(String name) {
      return TABLE;
    }

    @Override public Set<String> getTableNames() {
      return ImmutableSet.of("myTable");
    }

    @Override public RelProtoDataType getType(String name) {
      return null;
    }

    @Override public Set<String> getTypeNames() {
      return ImmutableSet.of();
    }

    @Override public Collection<Function> getFunctions(String name) {
      return null;
    }

    @Override public Set<String> getFunctionNames() {
      return ImmutableSet.of();
    }

    @Override public Schema getSubSchema(String name) {
      return null;
    }

    @Override public Set<String> getSubSchemaNames() {
      return ImmutableSet.of();
    }

    @Override public Expression getExpression(@Nullable SchemaPlus parentSchema, String name) {
      return null;
    }

    @Override public boolean isMutable() {
      return false;
    }

    @Override public Schema snapshot(SchemaVersion version) {
      return null;
    }
  };

  private static final Table TABLE = new Table() {
    /**
     * {@inheritDoc}
     *
     * <p>Table schema is as follows:
     *
     * <blockquote>
     * <pre>
     *  myTable(
     *          a: BIGINT,
     *          n1: STRUCT&lt;
     *                n11: STRUCT&lt;b: BIGINT&gt;,
     *                n12: STRUCT&lt;c: BIGINT&gt;
     *              &gt;,
     *          n2: STRUCT&lt;d: BIGINT&gt;,
     *          e: BIGINT)
     * </pre>
     * </blockquote>
     */
    @Override public RelDataType getRowType(RelDataTypeFactory tf) {
      RelDataType bigint = tf.createSqlType(SqlTypeName.BIGINT);
      RelDataType n1Type = tf.createStructType(
          ImmutableList.of(
              tf.createStructType(ImmutableList.of(bigint),
                  ImmutableList.of("b")),
              tf.createStructType(ImmutableList.of(bigint),
                  ImmutableList.of("c"))),
          ImmutableList.of("n11", "n12"));
      RelDataType n2Type = tf.createStructType(
          ImmutableList.of(bigint),
          ImmutableList.of("d"));
      return tf.createStructType(
          ImmutableList.of(bigint, n1Type, n2Type, bigint),
          ImmutableList.of("a", "n1", "n2", "e"));
    }

    @Override public Statistic getStatistic() {
      return STATS;
    }

    @Override public Schema.TableType getJdbcTableType() {
      return null;
    }

    @Override public boolean isRolledUp(String column) {
      return false;
    }

    @Override public boolean rolledUpColumnValidInsideAgg(
        String column,
        SqlCall call,
        @Nullable SqlNode parent,
        @Nullable CalciteConnectionConfig config) {
      return false;
    }
  };

  private static final Statistic STATS = new Statistic() {
    @Override public Double getRowCount() {
      return 0D;
    }
  };

  private static final SchemaPlus ROOT_SCHEMA = CalciteSchema
      .createRootSchema(false).add("myDb", SCHEMA).plus();

  private RelToSqlConverterTest.Sql sql(String sql) {
    return new RelToSqlConverterTest.Sql(ROOT_SCHEMA, sql,
        CalciteSqlDialect.DEFAULT, SqlParser.Config.DEFAULT,
        RelToSqlConverterTest.DEFAULT_REL_CONFIG, null, ImmutableList.of());
  }

  @Test void testNestedSchemaSelectStar() {
    String query = "SELECT * FROM \"myTable\"";
    String expected = "SELECT \"a\", "
        + "ROW(ROW(\"n1\".\"n11\".\"b\"), ROW(\"n1\".\"n12\".\"c\")) AS \"n1\", "
        + "ROW(\"n2\".\"d\") AS \"n2\", "
        + "\"e\"\n"
        + "FROM \"myDb\".\"myTable\"";
    sql(query).ok(expected);
  }

  @Test void testNestedSchemaRootColumns() {
    String query = "SELECT \"a\", \"e\" FROM \"myTable\"";
    String expected = "SELECT \"a\", "
        + "\"e\"\n"
        + "FROM \"myDb\".\"myTable\"";
    sql(query).ok(expected);
  }

  @Test void testNestedSchemaNestedColumns() {
    String query = "SELECT \"a\", \"e\", "
        + "\"myTable\".\"n1\".\"n11\".\"b\", "
        + "\"myTable\".\"n2\".\"d\" "
        + "FROM \"myTable\"";
    String expected = "SELECT \"a\", "
        + "\"e\", "
        + "\"n1\".\"n11\".\"b\", "
        + "\"n2\".\"d\"\n"
        + "FROM \"myDb\".\"myTable\"";
    sql(query).ok(expected);
  }
}
