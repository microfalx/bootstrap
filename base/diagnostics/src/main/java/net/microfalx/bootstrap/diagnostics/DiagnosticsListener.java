package net.microfalx.bootstrap.diagnostics;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.lang.Nameable;

import java.util.Collection;

/**
 * A listener used to collect diagnostics information from services.
 */
public interface DiagnosticsListener extends Nameable {

    /**
     * Invoked when the health for current process (service replica) needs to be calculated.
     *
     * @param health the health to update
     */
    void update(Health health);

    /**
     * Returns default thresholds used by this health contributor.
     *
     * @return a non-null instance
     */
    Collection<Thresholds> getThresholds();
}
