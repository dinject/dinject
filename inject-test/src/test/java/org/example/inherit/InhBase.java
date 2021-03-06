package org.example.inherit;

import jakarta.inject.Inject;
import org.example.coffee.core.Steamer;

class InhBase extends InhBaseBase {

  @Inject
  Steamer expectSetBaseField;

  private Steamer baseMethod;
  private boolean expectTrueBaseMethodCalled;
  private boolean expectTrueNoInjectOnOverride;

  @Override
  void baseBaseOverride(Steamer steamer) {
    expectTrueNoInjectOnOverride = true;
  }

  @Inject
  void baseBaseMethod(Steamer steamer) {
    expectTrueBaseMethodCalled = true;
  }

  @Inject
  @Override
  void baseMethod(Steamer steamer) {
    expectTrueBaseMethodCalled = true;
    this.baseMethod = steamer;
  }

  public Steamer getBaseMethod() {
    return baseMethod;
  }

  public Steamer expectSetBaseField() {
    return expectSetBaseField;
  }

  public boolean expectTrueBaseMethodCalled() {
    return expectTrueBaseMethodCalled;
  }

  public boolean expectTrueNoInjectOnOverride() {
    return expectTrueNoInjectOnOverride;
  }
}
