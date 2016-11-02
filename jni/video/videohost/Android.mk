LOCAL_PATH := $(call my-dir)
########################################
# NCI Configuration
########################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := android_middleware_VideoHost.cpp

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_CERTIFICATE := platform

LOCAL_SHARED_LIBRARIES := \
    libcutils libbinder libutils \
    liblog libandroid_runtime \
	libgui libvideoshare \

LOCAL_MODULE := libvideo_host_middleware
include $(BUILD_SHARED_LIBRARY)
