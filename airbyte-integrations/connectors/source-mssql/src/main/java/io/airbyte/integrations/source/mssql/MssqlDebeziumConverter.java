/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.source.mssql;

import com.microsoft.sqlserver.jdbc.Geography;
import com.microsoft.sqlserver.jdbc.Geometry;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import io.airbyte.cdk.db.DataTypeUtils;
import io.airbyte.cdk.db.jdbc.DateTimeConverter;
import io.airbyte.cdk.integrations.debezium.internals.DebeziumConverterUtils;
import io.debezium.spi.converter.CustomConverter;
import io.debezium.spi.converter.RelationalColumn;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import microsoft.sql.DateTimeOffset;
import org.apache.commons.codec.binary.Base64;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MssqlDebeziumConverter implements CustomConverter<SchemaBuilder, RelationalColumn> {

  private final Logger LOGGER = LoggerFactory.getLogger(MssqlDebeziumConverter.class);

  private final Set<String> BINARY = Set.of("VARBINARY", "BINARY");
  private final Set<String> DATETIME_TYPES = Set.of("DATETIME", "DATETIME2", "SMALLDATETIME");
  private final String DATE = "DATE";
  private static final String DATETIMEOFFSET = "DATETIMEOFFSET";
  private static final String TIME_TYPE = "TIME";
  private static final String SMALLMONEY_TYPE = "SMALLMONEY";
  private static final String GEOMETRY = "GEOMETRY";
  private static final String GEOGRAPHY = "GEOGRAPHY";
  private static final String DEBEZIUM_DATETIMEOFFSET_FORMAT = "yyyy-MM-dd HH:mm:ss[.][SSSSSSS] XXX";

  private static final String DATETIME_FORMAT_MICROSECONDS = "yyyy-MM-dd'T'HH:mm:ss[.][SSSSSS]";

  @Override
  public void configure(Properties props) {}

  @Override
  public void converterFor(final RelationalColumn field,
                           final ConverterRegistration<SchemaBuilder> registration) {
    if (DATE.equalsIgnoreCase(field.typeName())) {
      registerDate(field, registration);
    } else if (DATETIME_TYPES.contains(field.typeName().toUpperCase())) {
      registerDatetime(field, registration);
    } else if (SMALLMONEY_TYPE.equalsIgnoreCase(field.typeName())) {
      registerMoney(field, registration);
    } else if (BINARY.contains(field.typeName().toUpperCase())) {
      registerBinary(field, registration);
    } else if (GEOMETRY.equalsIgnoreCase(field.typeName())) {
      registerGeometry(field, registration);
    } else if (GEOGRAPHY.equalsIgnoreCase(field.typeName())) {
      registerGeography(field, registration);
    } else if (TIME_TYPE.equalsIgnoreCase(field.typeName())) {
      registerTime(field, registration);
    } else if (DATETIMEOFFSET.equalsIgnoreCase(field.typeName())) {
      registerDateTimeOffSet(field, registration);
    }
  }

  private void registerGeometry(final RelationalColumn field,
                                final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof byte[]) {
        try {
          return Geometry.deserialize((byte[]) input).toString();
        } catch (SQLServerException e) {
          LOGGER.error(e.getMessage());
        }
      }

      LOGGER.warn("Uncovered Geometry class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

  private void registerGeography(final RelationalColumn field,
                                 final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof byte[]) {
        try {
          return Geography.deserialize((byte[]) input).toString();
        } catch (SQLServerException e) {
          LOGGER.error(e.getMessage());
        }
      }

      LOGGER.warn("Uncovered Geography class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

  private void registerDate(final RelationalColumn field,
                            final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }
      if (field.typeName().equalsIgnoreCase("DATE")) {
        return DateTimeConverter.convertToDate(input);
      }
      return DateTimeConverter.convertToTimestamp(input);
    });
  }

  private void registerDatetime(final RelationalColumn field,
                                final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(),
        input -> {
          if (Objects.isNull(input)) {
            return DebeziumConverterUtils.convertDefaultValue(field);
          }

          final LocalDateTime localDateTime = ((Timestamp) input).toLocalDateTime();
          return localDateTime.format(DateTimeFormatter.ofPattern(DATETIME_FORMAT_MICROSECONDS));
        });

  }

  private void registerDateTimeOffSet(final RelationalColumn field,
                                      final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof DateTimeOffset) {
        return DataTypeUtils.toISO8601String(
            OffsetDateTime.parse(input.toString(),
                DateTimeFormatter.ofPattern(DEBEZIUM_DATETIMEOFFSET_FORMAT)));
      }

      LOGGER.warn("Uncovered DateTimeOffSet class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

  private void registerTime(final RelationalColumn field,
                            final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof Timestamp) {
        return DataTypeUtils.toISOTimeString(((Timestamp) input).toLocalDateTime());
      }

      LOGGER.warn("Uncovered time class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

  private void registerMoney(final RelationalColumn field,
                             final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.float64(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof BigDecimal) {
        return ((BigDecimal) input).doubleValue();
      }

      LOGGER.warn("Uncovered money class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

  private void registerBinary(final RelationalColumn field,
                              final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), input -> {
      if (Objects.isNull(input)) {
        return DebeziumConverterUtils.convertDefaultValue(field);
      }

      if (input instanceof byte[]) {
        return Base64.encodeBase64String((byte[]) input);
      }

      LOGGER.warn("Uncovered binary class type '{}'. Use default converter",
          input.getClass().getName());
      return input.toString();
    });
  }

}
