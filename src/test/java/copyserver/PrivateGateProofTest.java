package copyserver;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class PrivateGateProofTest {
  @Test
  void deliberatelyFailsToProveTheAggregateGateIsFailClosed() {
    fail("disposable negative-path proof");
  }
}
