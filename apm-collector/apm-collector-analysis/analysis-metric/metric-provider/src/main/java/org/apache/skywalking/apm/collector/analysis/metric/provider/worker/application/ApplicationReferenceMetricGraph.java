/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public ApplicationReferenceMetricGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<InstanceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(GraphIdDefine.APPLICATION_REFERENCE_METRIC_GRAPH_ID, InstanceReferenceMetric.class);

        graph.addNode(new ApplicationReferenceMetricAggregationWorker.Factory(moduleManager).create(workerCreateListener))
            .addNext(new ApplicationReferenceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, GraphIdDefine.APPLICATION_REFERENCE_METRIC_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationReferenceMetricPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        link(graph);
    }

    private void link(Graph<InstanceReferenceMetric> graph) {
        GraphManager.INSTANCE.findGraph(GraphIdDefine.INSTANCE_REFERENCE_METRIC_GRAPH_ID, InstanceReferenceMetric.class)
            .toFinder().findNode(WorkerIdDefine.INSTANCE_REFERENCE_METRIC_AGGREGATION_WORKER_ID, InstanceReferenceMetric.class)
            .addNext(new NodeProcessor<InstanceReferenceMetric, InstanceReferenceMetric>() {
                @Override public int id() {
                    return WorkerIdDefine.APPLICATION_REFERENCE_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override public void process(InstanceReferenceMetric instanceReferenceMetric,
                    Next<InstanceReferenceMetric> next) {
                    graph.start(instanceReferenceMetric);
                }
            });
    }
}
