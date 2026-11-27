#ifndef ONHAND_NNAPI_PROBE_COUNTS_H
#define ONHAND_NNAPI_PROBE_COUNTS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Android NDK DeviceTypeCode; checked against the NDK header by deviceprobe.c. */
enum {
    ONHAND_DEVICE_UNKNOWN = 0,
    ONHAND_DEVICE_OTHER = 1,
    ONHAND_DEVICE_CPU = 2,
    ONHAND_DEVICE_GPU = 3,
    ONHAND_DEVICE_ACCELERATOR = 4
};

struct ANeuralNetworksDevice;
typedef int (*GetDeviceCount)(uint32_t *);
typedef int (*GetDevice)(uint32_t, struct ANeuralNetworksDevice **);
typedef int (*GetDeviceType)(const struct ANeuralNetworksDevice *, int32_t *);

typedef struct {
    unsigned accelerators, gpu, cpu;
} OnHandDeviceCounts;

/* Publish counts only after every device query succeeds. Unknown and OTHER are
 * not accelerator evidence. Invalid enum values and partial reads fail closed. */
static inline bool onhand_nnapi_counts(GetDeviceCount get_count, GetDevice get_device,
                                     GetDeviceType get_type, OnHandDeviceCounts *out) {
    if (!get_count || !get_device || !get_type || !out) return false;
    uint32_t count = 0;
    if (get_count(&count) != 0 || count > 64) return false;
    OnHandDeviceCounts result = {0, 0, 0};
    for (uint32_t i = 0; i < count; ++i) {
        struct ANeuralNetworksDevice *device = NULL;
        int32_t type = -1;
        if (get_device(i, &device) != 0 || !device || get_type(device, &type) != 0)
            return false;
        switch (type) {
            case ONHAND_DEVICE_UNKNOWN:
            case ONHAND_DEVICE_OTHER: break;
            case ONHAND_DEVICE_CPU: ++result.cpu; break;
            case ONHAND_DEVICE_GPU: ++result.gpu; break;
            case ONHAND_DEVICE_ACCELERATOR: ++result.accelerators; break;
            default: return false;
        }
    }
    *out = result;
    return true;
}
#endif
