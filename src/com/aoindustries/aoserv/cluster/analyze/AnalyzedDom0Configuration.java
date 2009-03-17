/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.aoserv.cluster.analyze;

import com.aoindustries.aoserv.cluster.ClusterConfiguration;
import com.aoindustries.aoserv.cluster.Dom0;
import com.aoindustries.aoserv.cluster.Dom0Disk;
import com.aoindustries.aoserv.cluster.DomU;
import com.aoindustries.aoserv.cluster.DomUConfiguration;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.aoserv.cluster.UnmodifiableArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes a single Dom0 to find anything that is not optimal.
 * 
 * @author  AO Industries, Inc.
 */
public class AnalyzedDom0Configuration {

    private final ClusterConfiguration clusterConfiguration;
    private final Dom0 dom0;

    public AnalyzedDom0Configuration(ClusterConfiguration clusterConfiguration, Dom0 dom0) {
        this.clusterConfiguration = clusterConfiguration;
        this.dom0 = dom0;
    }

    public ClusterConfiguration getClusterConfiguration() {
        return clusterConfiguration;
    }

    public Dom0 getDom0() {
        return dom0;
    }

    /**
     * Gets the results for primary RAM
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getAvailableRamResult(ResultHandler<? super Integer> resultHandler, AlertLevel minimumAlertLevel) {
        int allocatedPrimaryRam = 0;
        for(DomUConfiguration domUConfiguration : clusterConfiguration.getDomUConfigurations()) {
            if(domUConfiguration.getPrimaryDom0()==dom0) allocatedPrimaryRam+=domUConfiguration.getDomU().getPrimaryRam();
        }
        int totalRam = dom0.getRam();
        int freePrimaryRam = totalRam - allocatedPrimaryRam;
        AlertLevel alertLevel = freePrimaryRam<0 ? AlertLevel.CRITICAL : AlertLevel.NONE;
        if(alertLevel.compareTo(minimumAlertLevel)>=0) {
            return resultHandler.handleResult(
                new Result<Integer>(
                    "Available RAM",
                    freePrimaryRam,
                    -((double)freePrimaryRam / (double)totalRam),
                    alertLevel
                )
            );
        } else return true;
    }

    /**
     * Gets the secondary RAM allocation results.  It has a separate
     * entry for each Dom0 that has any secondary resource on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getAllocatedSecondaryRamResults(ResultHandler<? super Integer> resultHandler, AlertLevel minimumAlertLevel) {
        if(minimumAlertLevel.compareTo(AlertLevel.HIGH)<=0) {
            int allocatedPrimaryRam = 0;
            Map<String,Integer> allocatedSecondaryRams = new HashMap<String,Integer>();
            for(DomUConfiguration domUConfiguration : clusterConfiguration.getDomUConfigurations()) {
                if(domUConfiguration.getPrimaryDom0()==dom0) {
                    allocatedPrimaryRam+=domUConfiguration.getDomU().getPrimaryRam();
                } else if(domUConfiguration.getSecondaryDom0()==dom0) {
                    int secondaryRam = domUConfiguration.getDomU().getSecondaryRam();
                    if(secondaryRam!=-1) {
                        String failedHostname = domUConfiguration.getPrimaryDom0().getHostname();
                        Integer totalSecondary = allocatedSecondaryRams.get(failedHostname);
                        allocatedSecondaryRams.put(
                            failedHostname,
                            totalSecondary==null ? secondaryRam : (totalSecondary+secondaryRam)
                        );
                    }
                }
            }
            int totalRam = dom0.getRam();
            int freePrimaryRam = totalRam - allocatedPrimaryRam;

            for(Map.Entry<String,Integer> entry : allocatedSecondaryRams.entrySet()) {
                String failedHostname = entry.getKey();
                int allocatedSecondary = entry.getValue();
                AlertLevel alertLevel = allocatedSecondary>freePrimaryRam ? AlertLevel.HIGH : AlertLevel.NONE;
                if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                    if(
                        !resultHandler.handleResult(
                            new Result<Integer>(
                                failedHostname,
                                allocatedSecondary,
                                (double)(allocatedSecondary-freePrimaryRam)/(double)totalRam,
                                alertLevel
                            )
                        )
                    ) return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the unmodifiable set of specific processor type results.  It has a
     * separate entry for each DomU that is either primary or secondary (with RAM)
     * on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getProcessorTypeResults(ResultHandler<? super ProcessorType> resultHandler, AlertLevel minimumAlertLevel) {
        if(minimumAlertLevel.compareTo(AlertLevel.LOW)<=0) {
            ProcessorType processorType = dom0.getProcessorType();

            List<DomUConfiguration> domUConfigurations = clusterConfiguration.getDomUConfigurations();
            for(DomUConfiguration domUConfiguration : domUConfigurations) {
                DomU domU = domUConfiguration.getDomU();
                if(
                    domUConfiguration.getPrimaryDom0()==dom0
                    || (
                        domUConfiguration.getSecondaryDom0()==dom0
                        && domU.getSecondaryRam()!=-1
                    )
                ) {
                    ProcessorType minProcessorType = domU.getMinimumProcessorType();
                    AlertLevel alertLevel = minProcessorType!=null && processorType.compareTo(minProcessorType)<0 ? AlertLevel.LOW : AlertLevel.NONE;
                    if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                        if(
                            !resultHandler.handleResult(
                                new Result<ProcessorType>(
                                    domU.getHostname(),
                                    minProcessorType,
                                    1,
                                    alertLevel
                                )
                            )
                        ) return false;
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * Gets the unmodifiable set of specific processor architecture results.  It has a
     * separate entry for each DomU that is either primary or secondary (with RAM)
     * on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getProcessorArchitectureResults(ResultHandler<? super ProcessorArchitecture> resultHandler, AlertLevel minimumAlertLevel) {
        ProcessorArchitecture processorArchitecture = dom0.getProcessorArchitecture();
        
        List<DomUConfiguration> domUConfigurations = clusterConfiguration.getDomUConfigurations();
        for(DomUConfiguration domUConfiguration : domUConfigurations) {
            DomU domU = domUConfiguration.getDomU();
            if(domUConfiguration.getPrimaryDom0()==dom0) {
                // Primary is CRITICAL
                ProcessorArchitecture minProcessorArchitecture = domU.getMinimumProcessorArchitecture();
                AlertLevel alertLevel = processorArchitecture.compareTo(minProcessorArchitecture)<0 ? AlertLevel.CRITICAL : AlertLevel.NONE;
                if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                    if(
                        !resultHandler.handleResult(
                            new Result<ProcessorArchitecture>(
                                domU.getHostname(),
                                minProcessorArchitecture,
                                1,
                                alertLevel
                            )
                        )
                    ) return false;
                }
            } else if(
                domUConfiguration.getSecondaryDom0()==dom0
                && domU.getSecondaryRam()!=-1
            ) {
                // Secondary is HIGH
                ProcessorArchitecture minProcessorArchitecture = domU.getMinimumProcessorArchitecture();
                AlertLevel alertLevel = processorArchitecture.compareTo(minProcessorArchitecture)<0 ? AlertLevel.HIGH : AlertLevel.NONE;
                if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                    if(
                        !resultHandler.handleResult(
                            new Result<ProcessorArchitecture>(
                                domU.getHostname(),
                                minProcessorArchitecture,
                                1,
                                alertLevel
                            )
                        )
                    ) return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the unmodifiable set of specific processor speed results.  It has a
     * separate entry for each DomU that is either primary or secondary (with RAM)
     * on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getProcessorSpeedResults(ResultHandler<? super Integer> resultHandler, AlertLevel minimumAlertLevel) {
        if(minimumAlertLevel.compareTo(AlertLevel.LOW)<=0) {
            int processorSpeed = dom0.getProcessorSpeed();

            List<DomUConfiguration> domUConfigurations = clusterConfiguration.getDomUConfigurations();
            for(DomUConfiguration domUConfiguration : domUConfigurations) {
                DomU domU = domUConfiguration.getDomU();
                if(
                    domUConfiguration.getPrimaryDom0()==dom0
                    || (
                        domUConfiguration.getSecondaryDom0()==dom0
                        && domU.getSecondaryRam()!=-1
                    )
                ) {
                    int minSpeed = domU.getMinimumProcessorSpeed();
                    AlertLevel alertLevel = minSpeed!=-1 && processorSpeed<minSpeed ? AlertLevel.LOW : AlertLevel.NONE;
                    if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                        if(
                            !resultHandler.handleResult(
                                new Result<Integer>(
                                    domU.getHostname(),
                                    minSpeed==-1 ? null : Integer.valueOf(minSpeed),
                                    (double)(minSpeed-processorSpeed)/(double)minSpeed,
                                    alertLevel
                                )
                            )
                        ) return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Gets the unmodifiable set of specific processor cores results.  It has a
     * separate entry for each DomU that is either primary or secondary (with RAM)
     * on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getProcessorCoresResults(ResultHandler<? super Integer> resultHandler, AlertLevel minimumAlertLevel) {
        if(minimumAlertLevel.compareTo(AlertLevel.MEDIUM)<=0) {
            int processorCores = dom0.getProcessorCores();

            List<DomUConfiguration> domUConfigurations = clusterConfiguration.getDomUConfigurations();
            for(DomUConfiguration domUConfiguration : domUConfigurations) {
                DomU domU = domUConfiguration.getDomU();
                if(
                    domUConfiguration.getPrimaryDom0()==dom0
                    || (
                        domUConfiguration.getSecondaryDom0()==dom0
                        && domU.getSecondaryRam()!=-1
                    )
                ) {
                    int minCores = domU.getProcessorCores();
                    AlertLevel alertLevel = minCores!=-1 && processorCores<minCores ? AlertLevel.MEDIUM : AlertLevel.NONE;
                    if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                        if(
                            !resultHandler.handleResult(
                                new Result<Integer>(
                                    domU.getHostname(),
                                    minCores==-1 ? null : Integer.valueOf(minCores),
                                    (double)(minCores-processorCores)/(double)minCores,
                                    alertLevel
                                )
                            )
                        ) return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Gets the free primary processor weight.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getAvailableProcessorWeightResult(ResultHandler<? super Integer> resultHandler, AlertLevel minimumAlertLevel) {
        if(minimumAlertLevel.compareTo(AlertLevel.MEDIUM)<=0) {
            int allocatedPrimaryWeight = 0;
            for(DomUConfiguration domUConfiguration : clusterConfiguration.getDomUConfigurations()) {
                if(domUConfiguration.getPrimaryDom0()==dom0) {
                    DomU domU = domUConfiguration.getDomU();
                    allocatedPrimaryWeight += (int)domU.getProcessorCores() * (int)domU.getProcessorWeight();
                }
            }
            int totalWeight = dom0.getProcessorCores() * 1024;
            int freePrimaryWeight = totalWeight - allocatedPrimaryWeight;
            AlertLevel alertLevel = freePrimaryWeight<0 ? AlertLevel.MEDIUM : AlertLevel.NONE;
            if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                return resultHandler.handleResult(
                    new Result<Integer>(
                        "Available Processor Weight",
                        freePrimaryWeight,
                        -((double)freePrimaryWeight / (double)totalWeight),
                        alertLevel
                    )
                );
            }
        }
        return true;
    }

    /**
     * Gets the unmodifiable list of specific requires HVM results.  It has a
     * separate entry for each DomU that is either primary or secondary (with RAM)
     * on this dom0.
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getRequiresHvmResults(ResultHandler<? super Boolean> resultHandler, AlertLevel minimumAlertLevel) {
        boolean supportsHvm = dom0.getSupportsHvm();
        List<DomUConfiguration> domUConfigurations = clusterConfiguration.getDomUConfigurations();
        for(DomUConfiguration domUConfiguration : domUConfigurations) {
            DomU domU = domUConfiguration.getDomU();
            if(domUConfiguration.getPrimaryDom0()==dom0) {
                boolean requiresHvm = domU.getRequiresHvm();
                AlertLevel alertLevel = requiresHvm && !supportsHvm ? AlertLevel.CRITICAL : AlertLevel.NONE;
                if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                    if(
                        !resultHandler.handleResult(
                            new Result<Boolean>(
                                domU.getHostname(),
                                requiresHvm,
                                1,
                                alertLevel
                            )
                        )
                    ) return false;
                }
            } else if(
                domUConfiguration.getSecondaryDom0()==dom0
                && domU.getSecondaryRam()!=-1
            ) {
                boolean requiresHvm = domU.getRequiresHvm();
                AlertLevel alertLevel = requiresHvm && !supportsHvm ? AlertLevel.HIGH : AlertLevel.NONE;
                if(alertLevel.compareTo(minimumAlertLevel)>=0) {
                    if(
                        !resultHandler.handleResult(
                            new Result<Boolean>(
                                domU.getHostname(),
                                requiresHvm,
                                1,
                                alertLevel
                            )
                        )
                    ) return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the unsorted, unmodifable list of results for each disk.
     */
    public List<AnalyzedDom0DiskConfiguration> getDom0Disks() {
        Map<String,Dom0Disk> clusterDom0Disks = dom0.getDom0Disks();
        int size = clusterDom0Disks.size();
        if(size==0) return Collections.emptyList();
        else if(size==1) {
            return Collections.singletonList(
                new AnalyzedDom0DiskConfiguration(clusterConfiguration, clusterDom0Disks.values().iterator().next())
            );
        } else {
            AnalyzedDom0DiskConfiguration[] array = new AnalyzedDom0DiskConfiguration[size];
            int index = 0;
            for(Dom0Disk dom0Disk : clusterDom0Disks.values()) {
                array[index++] = new AnalyzedDom0DiskConfiguration(clusterConfiguration, dom0Disk);
            }
            assert index==size : "index!=size: "+index+"!="+size;
            return new UnmodifiableArrayList<AnalyzedDom0DiskConfiguration>(array);
        }
    }

    /**
     * @see AnalyzedClusterConfiguration#getAllResults()
     *
     * @return true if more results are wanted, or false to receive no more results.
     */
    public boolean getAllResults(ResultHandler<Object> resultHandler, AlertLevel minimumAlertLevel) {
        if(!getAvailableRamResult(resultHandler, minimumAlertLevel)) return false;
        if(!getAllocatedSecondaryRamResults(resultHandler, minimumAlertLevel)) return false;
        if(!getProcessorTypeResults(resultHandler, minimumAlertLevel)) return false;
        if(!getProcessorArchitectureResults(resultHandler, minimumAlertLevel)) return false;
        if(!getProcessorSpeedResults(resultHandler, minimumAlertLevel)) return false;
        if(!getProcessorCoresResults(resultHandler, minimumAlertLevel)) return false;
        if(!getAvailableProcessorWeightResult(resultHandler, minimumAlertLevel)) return false;
        if(!getRequiresHvmResults(resultHandler, minimumAlertLevel)) return false;
        // The highest alert level for disks is HIGH, avoid ArrayList creation here
        if(minimumAlertLevel.compareTo(AlertLevel.HIGH)<=0) {
            for(AnalyzedDom0DiskConfiguration dom0Disk : getDom0Disks()) {
                if(!dom0Disk.getAllResults(resultHandler, minimumAlertLevel)) return false;
            }
        }
        return true;
    }
}
