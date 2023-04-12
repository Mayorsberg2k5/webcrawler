package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime start;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.start = ZonedDateTime.now(clock);
  }

  @Profiled
  private Boolean profiledClass(Class<?> klasse) throws IllegalArgumentException{
    List<Method> methods = new ArrayList(Arrays.asList(klasse.getDeclaredMethods()));
    return methods.isEmpty() ? false : methods.stream().anyMatch((method) -> method.getAnnotation(Profiled.class) != null);
  }

  public <T> T wrap(Class<T> klasse, T delegate) throws IllegalArgumentException{
    Objects.requireNonNull(klasse);

    // TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in a
    //       ProfilingMethodInterceptor and return a dynamic proxy from this method.
    //       See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.
    if (!this.profiledClass(klasse)) {
      throw new IllegalArgumentException(klasse.getName() + "does not have any profiled methods.");
    } else {
      ProfilingMethodInterceptor profilingInterceptor = new ProfilingMethodInterceptor(
              clock,
              delegate,
              state,
              start);
      Object proxy = Proxy.newProxyInstance(ProfilerImpl.class.getClassLoader(), new Class[]{klasse}, profilingInterceptor);
      return (T) proxy;
    }

  }

  public void writeData(Path path) {
    // TODO: Write the ProfilingState data to the given file path. If a file already exists at that
    //       path, the new data should be appended to the existing file.
    Objects.requireNonNull(path);

    try {
      BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      writeData(writer);
      writer.flush();
    } catch (IOException ioexception){
      ioexception.printStackTrace();
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(start));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
