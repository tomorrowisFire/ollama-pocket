#include <stdio.h>
#include <stdlib.h>
#include "../app/src/main/cpp/nnapi_probe_counts.h"

struct ANeuralNetworksDevice { int32_t type; unsigned index; };
static struct ANeuralNetworksDevice devices[64];
static uint32_t device_count;
static int count_error, device_error, null_device, type_error;
static unsigned checks;

static void check(bool value) {
    if (!value) { fprintf(stderr, "NNAPI classification check %u failed\n", checks); exit(1); }
    ++checks;
}
static int mock_count(uint32_t *out) { *out = device_count; return count_error; }
static int mock_device(uint32_t i, struct ANeuralNetworksDevice **out) {
    if (i >= device_count || i >= 64 || (int)i == device_error) return 1;
    *out = (int)i == null_device ? NULL : &devices[i];
    return 0;
}
static int mock_type(const struct ANeuralNetworksDevice *d, int32_t *out) {
    *out = d->type;
    return (int)d->index == type_error ? 1 : 0;
}
static void setup(uint32_t count, int32_t type) {
    device_count = count; count_error = 0; device_error = null_device = type_error = -1;
    for (unsigned i = 0; i < 64; ++i) { devices[i].type = type; devices[i].index = i; }
}
static void reject(void) {
    OnHandDeviceCounts out = {7, 8, 9};
    check(!onhand_nnapi_counts(mock_count, mock_device, mock_type, &out));
    check(out.accelerators == 7 && out.gpu == 8 && out.cpu == 9);
}
int main(void) {
    for (int32_t type = 0; type <= 4; ++type) {
        for (uint32_t n = 0; n <= 64; ++n) {
            setup(n, type);
            OnHandDeviceCounts out;
            check(onhand_nnapi_counts(mock_count, mock_device, mock_type, &out));
            check(out.accelerators == (type == 4 ? n : 0));
            check(out.gpu == (type == 3 ? n : 0));
            check(out.cpu == (type == 2 ? n : 0));
        }
    }
    setup(5, 0);
    for (int i = 0; i < 5; ++i) devices[i].type = i;
    OnHandDeviceCounts out;
    check(onhand_nnapi_counts(mock_count, mock_device, mock_type, &out));
    check(out.accelerators == 1 && out.gpu == 1 && out.cpu == 1);
    for (int i = 0; i < 64; ++i) {
        setup(64, 4); device_error = i; reject();
        setup(64, 4); null_device = i; reject();
        setup(64, 4); type_error = i; reject();
        setup(64, 4); devices[i].type = 5; reject();
        setup(64, 4); devices[i].type = -1; reject();
    }
    setup(65, 4); reject();
    setup(UINT32_MAX, 4); reject();
    setup(0, 0); count_error = 1; reject();
    check(!onhand_nnapi_counts(NULL, mock_device, mock_type, &out));
    check(!onhand_nnapi_counts(mock_count, NULL, mock_type, &out));
    check(!onhand_nnapi_counts(mock_count, mock_device, NULL, &out));
    check(!onhand_nnapi_counts(mock_count, mock_device, mock_type, NULL));
    printf("PASS: %u NNAPI classification checks\n", checks);
    return 0;
}
