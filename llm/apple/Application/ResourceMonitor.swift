/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

final class ResourceMonitor: ObservableObject {
  @Published var usedMemory = 0
  @Published var availableMemory = 0
  private var memoryUpdateTimer: Timer?

  deinit {
    stop()
  }

  public func start() {
    memoryUpdateTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
      self?.updateMemoryUsage()
    }
  }

  public func stop() {
    memoryUpdateTimer?.invalidate()
  }

  private func updateMemoryUsage() {
    usedMemory = usedMemoryInMB()
    availableMemory = availableMemoryInMB()
  }

  private func usedMemoryInMB() -> Int {
    var info = task_vm_info_data_t()
    var count = mach_msg_type_number_t(MemoryLayout<task_vm_info>.size) / 4

    let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
      $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
        task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
      }
    }
    guard kerr == KERN_SUCCESS else { return 0 }
    return Int(info.phys_footprint / 0x100000)
  }

  private func availableMemoryInMB() -> Int {
    #if os(iOS)
    return Int(os_proc_available_memory() / 0x100000)
    #elseif os(macOS)
    var stats = vm_statistics64()
    var count = mach_msg_type_number_t(MemoryLayout<vm_statistics64>.size / MemoryLayout<integer_t>.size)
    let result = withUnsafeMutablePointer(to: &stats) {
      $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
        host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &count)
      }
    }
    guard result == KERN_SUCCESS else { return 0 }
    let pageSize = vm_kernel_page_size
    let freeMemory = UInt64(stats.free_count) * UInt64(pageSize)
    return Int(freeMemory / 0x100000)
    #endif
  }
}
