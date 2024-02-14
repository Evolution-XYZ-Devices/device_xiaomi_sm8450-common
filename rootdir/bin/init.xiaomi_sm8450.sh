#!/vendor/bin/sh
#
# Copyright (C) 2024 Paranoid Android
# SPDX-License-Identifier: Apache-2.0
#

function write_irq_affinity() {
    # Arguments:
    # $1 = irq name
    # $2 = cpu id
    irq_dir="$(dirname /proc/irq/*/$1)"
    [ -d "$irq_dir" ] && echo $2 > "${irq_dir}/smp_affinity_list"
}

# IRQ Tuning
# kgsl_3d0_irq -> CPU 1
# msm_drm -> CPU 2
write_irq_affinity kgsl_3d0_irq 1
write_irq_affinity msm_drm 2
