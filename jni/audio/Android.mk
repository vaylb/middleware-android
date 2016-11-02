LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := android_middleware_AudioHost.cpp

LOCAL_SHARED_LIBRARIES := \
    libaudioflinger libbinder libutils \
    liblog libmedia libandroid_runtime libhostplay\

LOCAL_MODULE := libaudio_host_middleware

LOCAL_C_INCLUDES := \
		$(TOP)/hardware/qcom/audio/hal

include $(BUILD_SHARED_LIBRARY)
