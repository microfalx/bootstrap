package net.microfalx.bootstrap.support.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportServiceTest extends AbstractReportServiceTestCase {

    @Test
    void initialize() {
        assertEquals(4, reportService.getProviders().size());
    }

}