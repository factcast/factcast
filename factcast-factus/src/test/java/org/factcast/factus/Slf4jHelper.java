package org.factcast.factus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import slf4jtest.Settings;
import slf4jtest.TestLogger;
import slf4jtest.TestLoggerFactory;

@Value
public class Slf4jHelper {
  @SneakyThrows
  public static TestLogger replaceLogger(@NonNull Object instance) {
    Class<?> clazz = instance.getClass();
    Field field = clazz.getDeclaredField("log");
    field.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    TestLogger logger = new TestLoggerFactory(new Settings().enableAll()).getLogger(clazz);
    field.set(null, logger);
    return logger;
  }
}
