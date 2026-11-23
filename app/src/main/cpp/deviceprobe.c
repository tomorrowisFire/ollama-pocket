/* Query visible NNAPI device *types* without guessing an NPU from the SoC name.
 * Deprecated NNAPI is queried for diagnostics only; no inference is run here.
 * Dynamic resolution makes Android 8 / older driver stacks safe. */
#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>

typedef struct ANeuralNetworksDevice ANeuralNetworksDevice;
typedef int (*GetDeviceCount)(uint32_t *);
typedef int (*GetDevice)(uint32_t, ANeuralNetworksDevice **);
typedef int (*GetDeviceType)(const ANeuralNetworksDevice *, int32_t *);

JNIEXPORT jstring JNICALL
Java_com_ollamapocket_app_ComputeProfiler_nativeNnapiDevices(JNIEnv *env, jclass clazz) {
    (void)clazz;
    void *lib = dlopen("libneuralnetworks.so", RTLD_LAZY | RTLD_LOCAL);
    if (!lib) return (*env)->NewStringUTF(env, "");

    GetDeviceCount getCount = (GetDeviceCount)dlsym(lib, "ANeuralNetworks_getDeviceCount");
    GetDevice getDevice = (GetDevice)dlsym(lib, "ANeuralNetworks_getDevice");
    GetDeviceType getType = (GetDeviceType)dlsym(lib, "ANeuralNetworksDevice_getType");
    if (!getCount || !getDevice || !getType) {
        dlclose(lib);
        return (*env)->NewStringUTF(env, "");
    }

    uint32_t count = 0;
    if (getCount(&count) != 0 || count > 64) {
        dlclose(lib);
        return (*env)->NewStringUTF(env, "");
    }
    int accelerators = 0, gpu = 0, cpu = 0;
    for (uint32_t i = 0; i < count; i++) {
        ANeuralNetworksDevice *device = NULL;
        int32_t type = -1;
        if (getDevice(i, &device) != 0 || !device || getType(device, &type) != 0) continue;
        // NNAPI constants: OTHER=0 CPU=1 GPU=2 ACCELERATOR=3.
        if (type == 3) accelerators++;
        else if (type == 2) gpu++;
        else if (type == 1) cpu++;
    }
    dlclose(lib);
    char result[48];
    snprintf(result, sizeof(result), "%d,%d,%d", accelerators, gpu, cpu);
    return (*env)->NewStringUTF(env, result);
}
