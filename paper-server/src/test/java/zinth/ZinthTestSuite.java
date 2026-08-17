package zinth;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Runs the Zinth tests under {@code gradlew :paper-server:test}.
 *
 * <p>The test task only picks up classes matching {@code **&#47;**TestSuite.class}, and Paper's
 * five suites select only the {@code org.bukkit}, {@code io.papermc.paper} and
 * {@code com.destroystokyo.paper} packages — so without this file nothing under
 * {@code net.zanoria} would ever be discovered, and a Zinth test could fail forever without
 * turning anything red.
 *
 * <p>Deliberately carries no {@code @IncludeTags}: with no tag filter installed, untagged
 * tests run. Untagged also means no {@code @ExtendWith}, so none of Paper's environment
 * extensions fire and no Minecraft registry bootstrap happens — the Zinth tests are plain
 * unit tests and start in milliseconds.
 *
 * <p>It lives in package {@code zinth} rather than {@code net.zanoria.*} (a suite that selects
 * its own package would select itself) and rather than {@code org.bukkit.support.suite} (where
 * Paper's own suites would discover it as a nested suite).
 */
@Suite(failIfNoTests = false)
@SuiteDisplayName("Zinth — service wiring and behaviour")
@SelectPackages("net.zanoria")
public class ZinthTestSuite {
}
