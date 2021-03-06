package org.example.closable;

import jakarta.inject.Singleton;

@Singleton
public class ShouldCloseAuto2 implements AutoCloseable {

  private boolean closed;

  @Override
  public void close() throws Exception {
    closed = true;
  }

  boolean isClosed() {
    return closed;
  }
}
