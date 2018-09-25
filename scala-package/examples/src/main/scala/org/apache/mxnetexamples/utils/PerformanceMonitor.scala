/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mxnetexamples.utils

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}
import java.lang.management.ManagementFactory
import java.util.Date
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}
import sys.process._

class PerformanceMonitor(filename: String) {

  val bean = ManagementFactory.getOperatingSystemMXBean
    .asInstanceOf[com.sun.management.OperatingSystemMXBean]
  val runtime = Runtime.getRuntime

  val outputfile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename)))

  val csvSchema = Array("time", "processCpuLoad", "systemCpuLoad", "usedMemory", "freeMemory",
    "totalMemory", "maxMemory", "commitedVirtualMemory", "usedGPUMemory", "freeGPUMemory", "totalGPUMemory")
  outputfile.write(csvSchema.mkString(",") + "\n")

  var ex = new ScheduledThreadPoolExecutor(1)
  var task: Option[Task] = None
  var timestamp: Long = System.currentTimeMillis()

  def start(): Unit = {
    task = Some(Task(new Thread()))
    timestamp = System.currentTimeMillis
  }

  /**
    * Cleans up after thread has stopped monitoring
    */
  def stop() : Unit = {
    task.foreach(tsk => {
      outputfile.close()
      timestamp = System.currentTimeMillis
    })
  }

  case class Task(thread: Thread) {
    val future: ScheduledFuture[_ <: Any] = ex.scheduleAtFixedRate(thread, 1, 1, TimeUnit.SECONDS)
  }

  class Thread extends Runnable {
    /**
      * Runs a periodic measurement recording lines of performance and writing (buffered) to an
      * output CSV file
      */
    override def run(): Unit = {
      val time = new Date().getTime
      val processCpuLoad = bean.getProcessCpuLoad
      val systemCpuLoad = bean.getSystemCpuLoad
      val usedMemory = runtime.totalMemory - runtime.freeMemory
      val freeMemory = runtime.freeMemory
      val totalMemory = runtime.totalMemory
      val maxMemory = runtime.maxMemory
      val commitedVirtualMemory = bean.getCommittedVirtualMemorySize

      val gpuResult = ("nvidia-smi --format=csv" +
        "--query-gpu=memory.used,memory.free,memory.total,utilization.gpu").!!
      val IndexedSeq(usedGpuMemory, freeGpuMemory, totalGpuMemory, gpuUtilization) =
        if (gpuResult.contains("memory.used")) {
          val gpu = gpuResult.split("\n").tail.map(_.split(","))
          (0 until 4).map(i => gpu.map(_(i)).fold(0)(_ + _))
        } else {
          IndexedSeq(0, 0, 0, 0)
        }

      val row = Array(time, processCpuLoad, systemCpuLoad, usedMemory, freeMemory, totalMemory,
        maxMemory, commitedVirtualMemory, usedGpuMemory, freeGpuMemory, totalGpuMemory,
        gpuUtilization)
      outputfile.write(row.mkString(",") + "\n")
    }
  }

}
