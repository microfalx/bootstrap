package net.microfalx.bootstrap.support.report.fragment;

import net.microfalx.bootstrap.support.report.AbstractFragmentProvider;
import net.microfalx.bootstrap.support.report.Fragment;
import net.microfalx.bootstrap.support.report.Template;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.Server;
import net.microfalx.jvm.model.VirtualMachine;

import java.time.Duration;

@net.microfalx.lang.annotation.Provider
public class EnvironmentProvider extends AbstractFragmentProvider {

    @Override
    public Fragment create() {
        return Fragment.builder("Environment").template("environment")
                .icon("fa-solid fa-server")
                .order(1000)
                .build();
    }

    @Override
    public void update(Template template) {
        super.update(template);
        template.addVariable("environment", this);
    }

    public Server getServer() {
        return ServerMetrics.get().getLast();
    }

    public VirtualMachine getVirtualMachine() {
        return VirtualMachineMetrics.get().getLast();
    }

    public double getAverageServerCpu() {
        return ServerMetrics.get().getAverageTotalCpuSinceStartup();
    }

    public double getAverageServerMemory() {
        return ServerMetrics.get().getAverageMemorySinceStartup();
    }

    public double getAverageProcessCpu() {
        return VirtualMachineMetrics.get().getAverageCpuSinceStartup();
    }

    public double getAverageServerLoad15() {
        return ServerMetrics.get().getStore().getAverage(ServerMetrics.LOAD_15, Duration.ofDays(1)).orElse(0);
    }
}
