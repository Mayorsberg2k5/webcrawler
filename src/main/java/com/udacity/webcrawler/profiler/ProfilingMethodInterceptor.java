package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object object;
  private final ProfilingState state;
  private final ZonedDateTime start;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, Object object, ProfilingState state, ZonedDateTime start) {
    this.clock = Objects.requireNonNull(clock);
    this.object = Objects.requireNonNull(object);
    this.state = Objects.requireNonNull(state);
    this.start = Objects.requireNonNull(start);
  }

  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
    Instant start = null;
    boolean profiled = method.getAnnotation(Profiled.class) != null;

    // Checks if the method being called is profiled by checking if it has the @Profiled annotation.
    // if it is profiled, it records the time using the instant object.
    if (profiled) {
      start = clock.instant();
    }

    Object isinvoked;

    try {
      isinvoked = method.invoke(object, args);
    } catch (InvocationTargetException ite) {
      throw ite.getTargetException();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } finally {
      if (profiled) {
        Duration duration = Duration.between(start, clock.instant());
        state.record(object.getClass(), method, duration);
      }
    }

    return isinvoked;
  }


}




