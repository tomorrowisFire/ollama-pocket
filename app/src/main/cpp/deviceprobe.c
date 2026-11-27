/* Query visible NNAPI device types for diagnostics only, never inference.
 * Dynamic resolution makes older Android versions safe. */
#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <android/NeuralNetworksTypes.h>
#include "nnapi_probe_counts.h"

_Static_assert((int)ANEURALNETWORKS_DEVICE_UNKNOWN == ONHAND_DEVICE_UNKNOWN, "NNAPI UNKNOWN");
_Static_assert((int)ANEURALNETWORKS_DEVICE_OTHER == ONHAND_DEVICE_OTHER, "NNAPI OTHER");
_Static_assert((int)ANEURALNETWORKS_DEVICE_CPU == ONHAND_DEVICE_CPU, "NNAPI CPU");
_Static_assert((int)ANEURALNETWORKS_DEVICE_GPU == ONHAND_DEVICE_GPU, "NNAPI GPU");
_Static_assert((int)ANEURALNETWORKS_DEVICE_ACCELERATOR == ONHAND_DEVICE_ACCELERATOR, "NNAPI ACCELERATOR");

/* V2 prevents legacy probe binaries with incorrect device-type constants from
 * silently supplying misleading diagnostic data to this app candidate. */
JNIEXPORT jstring JNICALL
Java_com_ollamapocket_app_ComputeProfiler_nativeNnapiDevicesV2(JNIEnv *env, jclass clazz) {
    (void)clazz;
    void *lib = dlopen("libneuralnetworks.so", RTLD_LAZY | RTLD_LOCAL);
    if (!lib) return (*env)->NewStringUTF(env, "");
    GetDeviceCount get_count = (GetDeviceCount)dlsym(lib, "ANeuralNetworks_getDeviceCount");
    GetDevice get_device = (GetDevice)dlsym(lib, "ANeuralNetworks_getDevice");
    GetDeviceType get_type = (GetDeviceType)dlsym(lib, "ANeuralNetworksDevice_getType");
    OnHandDeviceCounts counts;
    bool ok = onhand_nnapi_counts(get_count, get_device, get_type, &counts);
    dlclose(lib);
    if (!ok) return (*env)->NewStringUTF(env, "");
    char result[48];
    snprintf(result, sizeof(result), "%u,%u,%u", counts.accelerators, counts.gpu, counts.cpu);
    return (*env)->NewStringUTF(env, result);
}
