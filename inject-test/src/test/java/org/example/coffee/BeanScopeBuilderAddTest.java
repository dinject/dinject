package org.example.coffee;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.Builder;
import io.avaje.inject.spi.Module;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

public class BeanScopeBuilderAddTest {

  @Test
  public void withModules_excludingThisOne() {
    TDPump testDoublePump = new TDPump();
    try (BeanScope context = BeanScope.newBuilder()
      .withBeans(testDoublePump)
      // our module is "org.example.coffee"
      // so this effectively includes no modules
      .withModules(new SillyModule())
      .build()) {

      CoffeeMaker coffeeMaker = context.get(CoffeeMaker.class);
      assertThat(coffeeMaker).isNull();
    }
  }

  static class SillyModule implements Module {

    @Override
    public Class<?>[] requires() {
      return new Class[0];
    }

    @Override
    public Class<?>[] provides() {
      return new Class[0];
    }

    @Override
    public void build(Builder builder) {
      // do nothing
    }
  }

  @Test
  public void withModules_includeThisOne() {

    TDPump testDoublePump = new TDPump();

    try (BeanScope context = BeanScope.newBuilder()
      .withBeans(testDoublePump)
      .withModules(new org.example.ExampleModule())
      .build()) {

      String makeIt = context.get(CoffeeMaker.class).makeIt();
      assertThat(makeIt).isEqualTo("done");

      assertThat(testDoublePump.steam).isEqualTo(1);
      assertThat(testDoublePump.water).isEqualTo(1);
    }
  }

  @Test
  public void withBean_expect_testDoublePumpUsed() {

    TDPump testDoublePump = new TDPump();

    try (BeanScope context = BeanScope.newBuilder()
      .withBeans(testDoublePump)
      .build()) {

      String makeIt = context.get(CoffeeMaker.class).makeIt();
      assertThat(makeIt).isEqualTo("done");

      assertThat(testDoublePump.steam).isEqualTo(1);
      assertThat(testDoublePump.water).isEqualTo(1);
    }
  }

  @Test
  public void withMockitoMock_expect_mockUsed() {

    Pump mock = Mockito.mock(Pump.class);

    try (BeanScope context = BeanScope.newBuilder()
      .withBean(Pump.class, mock)
      .build()) {

      Pump pump = context.get(Pump.class);
      assertThat(pump).isSameAs(mock);

      CoffeeMaker coffeeMaker = context.get(CoffeeMaker.class);
      assertThat(coffeeMaker).isNotNull();
      coffeeMaker.makeIt();

      verify(pump).pumpSteam();
      verify(pump).pumpWater();
    }
  }

  /**
   * Our test double that we want to wire.
   */
  static class TDPump implements Pump {

    int water;
    int steam;

    @Override
    public void pumpWater() {
      water++;
    }

    @Override
    public void pumpSteam() {
      steam++;
    }
  }
}
