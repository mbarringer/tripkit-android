package com.skedgo.android.tripkit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class ConfigsTest {
  /**
   * Only regionEligibility and context are mandatory.
   * Others are optional, and debuggable is false by default.
   */
  @Test public void build() {
    final Configs configs = Configs.builder()
        .regionEligibility("")
        .context(RuntimeEnvironment.application)
        .build();
    assertThat(configs.debuggable()).isFalse();
  }

  @Test(expected = NullPointerException.class)
  public void regionEligibilityIsNonNull() {
    Configs.builder()
        .regionEligibility(null)
        .context(RuntimeEnvironment.application)
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void contextIsNonNull() {
    Configs.builder()
        .regionEligibility("")
        .context(null)
        .build();
  }
}