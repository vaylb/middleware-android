#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>


#include "screenrecord.h"

extern "C" {
	#include "jpeglib.h" 
}
#define COMPRESS_QUALITY 80


namespace android{

	static const uint32_t kMinBitRate = 100000;         // 0.1Mbps
	static const uint32_t kMaxBitRate = 200 * 1000000;  // 200Mbps
	static const uint32_t kMaxTimeLimitSec = 30;       // 1 minutes
	static const uint32_t kFallbackWidth = 1080;        // 1080p
	static const uint32_t kFallbackHeight = 1920;
	static char* kMimeTypeAvc ="video/avc";

	// Command-line parameters.
	static bool gVerbose;           // chatty on stdout
	static bool gRotate;            // rotate 90 degrees
	static enum {
	    FORMAT_MP4, FORMAT_H264, FORMAT_FRAMES, FORMAT_RAW_FRAMES
	} gOutputFormat;           // data format for output
	static bool gSizeSpecified;     // was size explicitly requested?
	static bool gWantInfoScreen;    // do we want initial info screen?
	static bool gWantFrameTime;     // do we want times on each frame?
	static uint32_t gVideoWidth;        // default width+height
	static uint32_t gVideoHeight;
	static uint32_t gBitRate;     // 4Mbps
	static uint32_t gTimeLimitSec;

	// Set by signal handler to stop recording.
	static volatile bool gStopRequested;

	//socket
	static		int							mSlaveNum;
	static Vector<int> 						mSlaveSockets;
	static		int							mServerSocketFd;
	static		int 						mServerAddrLen;
	static		struct sockaddr_in 			mServerAddress;

	static		Mutex						mTransmitLock;
	static		Condition					mDataTransmit;

int socket_write(int fd,unsigned char *buffer,int length) 
{ 
	int bytes_left; 
	int written_bytes; 
	unsigned char *ptr; 

	ptr=buffer; 
	bytes_left=length; 
	while(bytes_left>0) 
	{ 
         written_bytes=write(fd,ptr,bytes_left); 
         if(written_bytes<=0)
         {        
			if(errno==EINTR) written_bytes=0; 
			else return -1; 
         } 
         bytes_left-=written_bytes; 
         ptr+=written_bytes;     
	} 
	return 0; 
} 

//----------------------------------------------------------
//JPEG compress

/* The following declarations and 5 functions are jpeg related  functions */  
typedef struct {  
    struct jpeg_destination_mgr pub;  
    JOCTET *buf;  
    size_t bufsize;  
    size_t jpegsize;  
} mem_destination_mgr;  
    
typedef mem_destination_mgr *mem_dest_ptr;  
    
    
void init_destination(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->pub.next_output_byte = dest->buf;  
    dest->pub.free_in_buffer = dest->bufsize;  
    dest->jpegsize = 0;  
}  
    
boolean empty_output_buffer(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->pub.next_output_byte = dest->buf;  
    dest->pub.free_in_buffer = dest->bufsize;  
    
    return FALSE;  
}  
    
void term_destination(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    dest->jpegsize = dest->bufsize - dest->pub.free_in_buffer;  
}  
    
static void jpeg_mem_dest(j_compress_ptr cinfo, JOCTET* buf, size_t bufsize)  
{  
    mem_dest_ptr dest;  
    
    if (cinfo->dest == NULL) {  
        cinfo->dest = (struct jpeg_destination_mgr *)  
            (*cinfo->mem->alloc_small)((j_common_ptr)cinfo, JPOOL_PERMANENT,  
            sizeof(mem_destination_mgr));  
    }  
    
    dest = (mem_dest_ptr) cinfo->dest;  
    
    dest->pub.init_destination    = init_destination;  
    dest->pub.empty_output_buffer = empty_output_buffer;  
    dest->pub.term_destination    = term_destination;  
    
    dest->buf      = buf;  
    dest->bufsize  = bufsize;  
    dest->jpegsize = 0;  
}  
    
static int jpeg_mem_size(j_compress_ptr cinfo)  
{  
    mem_dest_ptr dest = (mem_dest_ptr) cinfo->dest;  
    return dest->jpegsize;  
}
//------------------------- JPEG compress end ---------------------------------



/*
 * Parses args and kicks things off.
 */
ScreenRecord::ScreenRecord(const char* size,const char* bitrate, bool rotate, const char* format, const char* filepath)
{
	ALOGE("vaylb-->ScreenRecord constructor");
	gVerbose = true;
	gRotate = false;
	gWantInfoScreen = false;
	gWantFrameTime = false;
	gOutputFormat = FORMAT_MP4;
	gSizeSpecified = false;
	gWantInfoScreen = false;
	gWantFrameTime = false;
	gVideoWidth = 0;
	gVideoHeight = 0;
	gBitRate = 4000000;
	gTimeLimitSec = kMaxTimeLimitSec;
	gStopRequested = false;

	
    if (size != NULL && !parseWidthHeight(size, &gVideoWidth, &gVideoHeight)) {
		ALOGE("vaylb-->Invalid size '%s', must be width x height, set default width height\n",size);
        gVideoWidth = kFallbackWidth;
		gVideoHeight = kFallbackHeight;
    }
    if (gVideoWidth == 0 || gVideoHeight == 0) {
		ALOGE("vaylb-->Invalid size %ux%u, width and height may not be zero\n",gVideoWidth, gVideoHeight);
        return;
    }
	ALOGE("vaylb-->gVideoWidth %d, gVideoHeight %d",gVideoWidth, gVideoHeight);
    gSizeSpecified = true;
    if (parseValueWithUnit(bitrate, &gBitRate) != NO_ERROR) {
		ALOGE("vaylb-->Invalid bitrate '%s', set default\n",bitrate);
		gBitRate = kMinBitRate;
    }
    if (gBitRate < kMinBitRate || gBitRate > kMaxBitRate) {
		ALOGE("vaylb-->Bit rate %dbps outside acceptable range [%d,%d]\n",gBitRate, kMinBitRate, kMaxBitRate);
        return;
    }

    if(rotate){
		ALOGE("screenrecord gRotate true");
		gRotate = true;
	}
	
	if(format != NULL){
		if (strcmp(format, "mp4") == 0) {
                gOutputFormat = FORMAT_MP4;
        } else if (strcmp(format, "h264") == 0) {
            gOutputFormat = FORMAT_H264;
        } else if (strcmp(format, "frames") == 0) {
            gOutputFormat = FORMAT_FRAMES;
        } else if (strcmp(format, "raw-frames") == 0) {
            gOutputFormat = FORMAT_RAW_FRAMES;
        } else {
            ALOGE("vaylb-->Unknown format '%s'\n", format);
            return;
        }
	}
	ALOGE("vaylb-->video format '%s'\n", format);

    const char* fileName = filepath;
    if (gOutputFormat == FORMAT_MP4) {
        // MediaMuxer tries to create the file in the constructor, but we don't
        // learn about the failure until muxer.start(), which returns a generic
        // error code without logging anything.  We attempt to create the file
        // now for better diagnostics.
        int fd = open(fileName, O_CREAT | O_RDWR, 0644);
        if (fd < 0) {
            ALOGE("vaylb-->Unable to open '%s': %s\n", fileName, strerror(errno));
            return;
        }
        close(fd);
    }

	mSendAddr = malloc(gVideoWidth*gVideoHeight*3);
	mCompressAddr = malloc(gVideoWidth*gVideoHeight*3);
	mSendBuffer = new DataBuffer(gVideoWidth*gVideoHeight*9+sizeof(int)*3); //three buffer size

	mVideoTransmitor = new VideoTransmitor(this);

    //status_t err = recordScreen(fileName);
}


ScreenRecord::~ScreenRecord(){
	ALOGE("vaylb-->ScreenRecord destructor");
	if(mSendAddr != NULL){
		free(mSendAddr);
	}

	if(mCompressAddr != NULL){
		free(mCompressAddr);
	}
}

void ScreenRecord::start_threads(){
	if(mVideoTransmitor != NULL) mVideoTransmitor->threadLoop_run();
}

void ScreenRecord::stop_threads(){
	if(mVideoTransmitor != NULL) mVideoTransmitor->threadLoop_exit();
	signalDataTransmitor();
}



/*
 * Returns "true" if the device is rotated 90 degrees.
 */
bool ScreenRecord::isDeviceRotated(int orientation) {
    return orientation != DISPLAY_ORIENTATION_0 &&
            orientation != DISPLAY_ORIENTATION_180;
}

/*
 * Configures and starts the MediaCodec encoder.  Obtains an input surface
 * from the codec.
 */
status_t ScreenRecord::prepareEncoder(float displayFps, sp<MediaCodec>* pCodec,
        sp<IGraphicBufferProducer>* pBufferProducer) {
    status_t err;

    if (gVerbose) {
        ALOGE("vaylb-->Configuring recorder for %dx%d %s at %.2fMbps\n",
                gVideoWidth, gVideoHeight, kMimeTypeAvc, gBitRate / 1000000.0);
    }

    sp<AMessage> format = new AMessage;
    format->setInt32("width", gVideoWidth);
    format->setInt32("height", gVideoHeight);
    format->setString("mime", kMimeTypeAvc);
    format->setInt32("color-format", OMX_COLOR_FormatAndroidOpaque);
    format->setInt32("bitrate", gBitRate);
    format->setFloat("frame-rate", displayFps);
    format->setInt32("i-frame-interval", 10);

    sp<ALooper> looper = new ALooper;
    looper->setName("middleware_screenrecord_looper");
    looper->start();
    ALOGE("vaylb-->Creating codec");
    sp<MediaCodec> codec = MediaCodec::CreateByType(looper, kMimeTypeAvc, true);
    if (codec == NULL) {
        ALOGE("vaylb-->ERROR: unable to create %s codec instance\n",
                kMimeTypeAvc);
        return UNKNOWN_ERROR;
    }

    err = codec->configure(format, NULL, NULL,
            MediaCodec::CONFIGURE_FLAG_ENCODE);
    if (err != NO_ERROR) {
        ALOGE("vaylb-->ERROR: unable to configure %s codec at %dx%d (err=%d)\n",
                kMimeTypeAvc, gVideoWidth, gVideoHeight, err);
        codec->release();
        return err;
    }

    ALOGE("vaylb-->Creating encoder input surface");
    sp<IGraphicBufferProducer> bufferProducer;
    err = codec->createInputSurface(&bufferProducer);
    if (err != NO_ERROR) {
        ALOGE("vaylb-->ERROR: unable to create encoder input surface (err=%d)\n", err);
        codec->release();
        return err;
    }

    ALOGE("vaylb-->Starting codec");
    err = codec->start();
    if (err != NO_ERROR) {
        ALOGE("vaylb-->ERROR: unable to start codec (err=%d)\n", err);
        codec->release();
        return err;
    }

    ALOGE("vaylb-->Codec prepared");
    *pCodec = codec;
    *pBufferProducer = bufferProducer;
    return 0;
}

/*
 * Sets the display projection, based on the display dimensions, video size,
 * and device orientation.
 */
status_t ScreenRecord::setDisplayProjection(const sp<IBinder>& dpy,
        const DisplayInfo& mainDpyInfo) {
    status_t err;

    // Set the region of the layer stack we're interested in, which in our
    // case is "all of it".  If the app is rotated (so that the width of the
    // app is based on the height of the display), reverse width/height.
    bool deviceRotated = isDeviceRotated(mainDpyInfo.orientation);
    uint32_t sourceWidth, sourceHeight;
    if (!deviceRotated) {
        sourceWidth = mainDpyInfo.w;
        sourceHeight = mainDpyInfo.h;
    } else {
        ALOGE("vaylb-->using rotated width/height");
        sourceHeight = mainDpyInfo.w;
        sourceWidth = mainDpyInfo.h;
    }
    Rect layerStackRect(sourceWidth, sourceHeight);

    // We need to preserve the aspect ratio of the display.
    float displayAspect = (float) sourceHeight / (float) sourceWidth;


    // Set the way we map the output onto the display surface (which will
    // be e.g. 1280x720 for a 720p video).  The rect is interpreted
    // post-rotation, so if the display is rotated 90 degrees we need to
    // "pre-rotate" it by flipping width/height, so that the orientation
    // adjustment changes it back.
    //
    // We might want to encode a portrait display as landscape to use more
    // of the screen real estate.  (If players respect a 90-degree rotation
    // hint, we can essentially get a 720x1280 video instead of 1280x720.)
    // In that case, we swap the configured video width/height and then
    // supply a rotation value to the display projection.
    uint32_t videoWidth, videoHeight;
    uint32_t outWidth, outHeight;
    if (!gRotate) {
        videoWidth = gVideoWidth;
        videoHeight = gVideoHeight;
    } else {
        videoWidth = gVideoHeight;
        videoHeight = gVideoWidth;
    }
	
	#if 0
    if (videoHeight > (uint32_t)(videoWidth * displayAspect)) {
        // limited by narrow width; reduce height
        outWidth = videoWidth;
        //outHeight = (uint32_t)(videoWidth * displayAspect);
    } else {
        // limited by short height; restrict width
        outHeight = videoHeight;
        //outWidth = (uint32_t)(videoHeight / displayAspect);
        outWidth = videoWidth;
    }

	uint32_t offX, offY;
    offX = (videoWidth - outWidth) / 2;
    offY = (videoHeight - outHeight) / 2;
    Rect displayRect(offX, offY, offX + outWidth, offY + outHeight);
	#endif

	if (!deviceRotated) {
        outWidth = videoWidth;
		outHeight = videoHeight;
    } else {
        outWidth = videoHeight;
		outHeight = videoWidth;
    }
	
    Rect displayRect(0, 0, outWidth, outHeight);

    if (gVerbose) {
        if (gRotate) {
            ALOGE("vaylb-->Rotated content area is %ux%u at offset x=%d y=%d\n",
                    outHeight, outWidth, 0, 0);
        } else {
            ALOGE("vaylb-->Content area is %ux%u at offset x=%d y=%d\n",
                    outWidth, outHeight, 0, 0);
        }
    }

    SurfaceComposerClient::setDisplayProjection(dpy,
            gRotate ? DISPLAY_ORIENTATION_90 : DISPLAY_ORIENTATION_0,
            layerStackRect, displayRect);
    return NO_ERROR;
}

/*
 * Configures the virtual display.  When this completes, virtual display
 * frames will start arriving from the buffer producer.
 */
status_t ScreenRecord::prepareVirtualDisplay(const DisplayInfo& mainDpyInfo,
        const sp<IGraphicBufferProducer>& bufferProducer,
        sp<IBinder>* pDisplayHandle) {
    if(*pDisplayHandle == NULL){
		sp<IBinder> dpy = SurfaceComposerClient::createDisplay(
				String8("ScreenRecorder"), false /*secure*/);
	
		SurfaceComposerClient::openGlobalTransaction();
		SurfaceComposerClient::setDisplaySurface(dpy, bufferProducer);
		setDisplayProjection(dpy, mainDpyInfo);
		SurfaceComposerClient::setDisplayLayerStack(dpy, 0);	// default stack
		SurfaceComposerClient::closeGlobalTransaction();

		*pDisplayHandle = dpy;
	}else{
		*pDisplayHandle = NULL;
		sp<IBinder> dpy = SurfaceComposerClient::createDisplay(
				String8("ScreenRecorder_Rotate"), false /*secure*/);
	
		SurfaceComposerClient::openGlobalTransaction();
		SurfaceComposerClient::setDisplaySurface(dpy, bufferProducer);
		setDisplayProjection(dpy, mainDpyInfo);
		SurfaceComposerClient::setDisplayLayerStack(dpy, 0);	// default stack
		SurfaceComposerClient::closeGlobalTransaction();

		*pDisplayHandle = dpy;
	
		//SurfaceComposerClient::openGlobalTransaction();
		//SurfaceComposerClient::setDisplaySurface(*pDisplayHandle, bufferProducer);
		//setDisplayProjection(*pDisplayHandle, mainDpyInfo);
		//SurfaceComposerClient::closeGlobalTransaction();
	}

    return NO_ERROR;
}

/*
 * Runs the MediaCodec encoder, sending the output to the MediaMuxer.  The
 * input frames are coming from the virtual display as fast as SurfaceFlinger
 * wants to send them.
 *
 * Exactly one of muxer or rawFp must be non-null.
 *
 * The muxer must *not* have been started before calling.
 */
status_t ScreenRecord::runEncoder(const sp<MediaCodec>& encoder,
        const sp<MediaMuxer>& muxer, FILE* rawFp, const sp<IBinder>& mainDpy,
        const sp<IBinder>& virtualDpy, uint8_t orientation) {
    static int kTimeout = 250000;   // be responsive on signal
    status_t err;
    ssize_t trackIdx = -1;
    uint32_t debugNumFrames = 0;
    int64_t startWhenNsec = systemTime(CLOCK_MONOTONIC);
    int64_t endWhenNsec = startWhenNsec + seconds_to_nanoseconds(gTimeLimitSec);
    DisplayInfo mainDpyInfo;

    assert((rawFp == NULL && muxer != NULL) || (rawFp != NULL && muxer == NULL));

    Vector<sp<ABuffer> > buffers;
    err = encoder->getOutputBuffers(&buffers);
    if (err != NO_ERROR) {
        ALOGE("vaylb-->Unable to get output buffers (err=%d)\n", err);
        return err;
    }

    // This is set by the signal handler.
    gStopRequested = false;

    // Run until we're signaled.
    while (!gStopRequested) {
        size_t bufIndex, offset, size;
        int64_t ptsUsec;
        uint32_t flags;

		/*
	        if (systemTime(CLOCK_MONOTONIC) > endWhenNsec) {
	            if (gVerbose) {
	                ALOGE("vaylb-->Time limit reached\n");
	            }
	            break;
	        }
		*/

        ALOGE("vaylb-->Calling dequeueOutputBuffer");
        err = encoder->dequeueOutputBuffer(&bufIndex, &offset, &size, &ptsUsec,
                &flags, kTimeout);
        ALOGE("vaylb-->dequeueOutputBuffer returned %d", err);
        switch (err) {
        case NO_ERROR:
            // got a buffer
            if ((flags & MediaCodec::BUFFER_FLAG_CODECCONFIG) != 0) {
                ALOGE("vaylb-->Got codec config buffer (%zu bytes)", size);
                if (muxer != NULL) {
                    // ignore this -- we passed the CSD into MediaMuxer when
                    // we got the format change notification
                    size = 0;
                }
            }
            if (size != 0) {
                ALOGE("vaylb-->Got data in buffer %zu, size=%zu, pts=%" PRId64,
                        bufIndex, size, ptsUsec);

                { // scope
                    ATRACE_NAME("orientation");
                    // Check orientation, update if it has changed.
                    //
                    // Polling for changes is inefficient and wrong, but the
                    // useful stuff is hard to get at without a Dalvik VM.
                    err = SurfaceComposerClient::getDisplayInfo(mainDpy,
                            &mainDpyInfo);
                    if (err != NO_ERROR) {
                        ALOGW("vaylb-->getDisplayInfo(main) failed: %d", err);
                    } else if (orientation != mainDpyInfo.orientation) {
                        ALOGE("vaylb-->orientation changed, now %d", mainDpyInfo.orientation);
                        SurfaceComposerClient::openGlobalTransaction();
                        setDisplayProjection(virtualDpy, mainDpyInfo);
                        SurfaceComposerClient::closeGlobalTransaction();
						orientation = mainDpyInfo.orientation;
                    }
                }

                // If the virtual display isn't providing us with timestamps,
                // use the current time.  This isn't great -- we could get
                // decoded data in clusters -- but we're not expecting
                // to hit this anyway.
                if (ptsUsec == 0) {
                    ptsUsec = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;
                }

                if (muxer == NULL) {
					// TODO:write data to socket
					
					Vector<int>::iterator curr = mSlaveSockets.begin();
					Vector<int>::iterator end = mSlaveSockets.end();
					//int send = htonl(size+sizeof(int));
					while(curr!=end){
						//int res = write(*curr,(const void*)&send,sizeof(size));
						//if(res == -1)
						//{
						//	ALOGE("vaylb-->screenrecord write socket error %d, fd = %d",errno,*curr);
						//	curr++;
						//	continue;
						//}
						socket_write(*curr,buffers[bufIndex]->data(),size);
						curr++;
					}
					
                    //fwrite(buffers[bufIndex]->data(), 1, size, rawFp);
                    // Flush the data immediately in case we're streaming.
                    // We don't want to do this if all we've written is
                    // the SPS/PPS data because mplayer gets confused.

					//if ((flags & MediaCodec::BUFFER_FLAG_CODECCONFIG) == 0) {
                    //    fflush(rawFp);
                    //}
                } else {
                    // The MediaMuxer docs are unclear, but it appears that we
                    // need to pass either the full set of BufferInfo flags, or
                    // (flags & BUFFER_FLAG_SYNCFRAME).
                    //
                    // If this blocks for too long we could drop frames.  We may
                    // want to queue these up and do them on a different thread.
                    ATRACE_NAME("write sample");
                    assert(trackIdx != -1);
                    err = muxer->writeSampleData(buffers[bufIndex], trackIdx,
                            ptsUsec, flags);
                    if (err != NO_ERROR) {
                        ALOGE("vaylb-->Failed writing data to muxer (err=%d)\n", err);
                        return err;
                    }
                }
                debugNumFrames++;
            }
            err = encoder->releaseOutputBuffer(bufIndex);
            if (err != NO_ERROR) {
                ALOGE("vaylb-->Unable to release output buffer (err=%d)\n",err);
                return err;
            }
            if ((flags & MediaCodec::BUFFER_FLAG_EOS) != 0) {
                // Not expecting EOS from SurfaceFlinger.  Go with it.
                ALOGI("vaylb-->Received end-of-stream");
                gStopRequested = true;
            }
            break;
        case -EAGAIN:                       // INFO_TRY_AGAIN_LATER
            ALOGE("vaylb-->Got -EAGAIN, looping");
            break;
        case INFO_FORMAT_CHANGED:           // INFO_OUTPUT_FORMAT_CHANGED
            {
                // Format includes CSD, which we must provide to muxer.
                ALOGE("vaylb-->Encoder format changed");
                sp<AMessage> newFormat;
                encoder->getOutputFormat(&newFormat);
                if (muxer != NULL) {
                    trackIdx = muxer->addTrack(newFormat);
                    ALOGE("Starting muxer");
                    err = muxer->start();
                    if (err != NO_ERROR) {
                        fprintf(stderr, "Unable to start muxer (err=%d)\n", err);
                        return err;
                    }
                }
            }
            break;
        case INFO_OUTPUT_BUFFERS_CHANGED:   // INFO_OUTPUT_BUFFERS_CHANGED
            // Not expected for an encoder; handle it anyway.
            ALOGE("vaylb-->Encoder buffers changed");
            err = encoder->getOutputBuffers(&buffers);
            if (err != NO_ERROR) {
                ALOGE("vaylb-->Unable to get new output buffers (err=%d)\n", err);
                return err;
            }
            break;
        case INVALID_OPERATION:
            ALOGE("vaylb-->dequeueOutputBuffer returned INVALID_OPERATION");
            return err;
        default:
            ALOGE("vaylb-->Got weird result %d from dequeueOutputBuffer\n", err);
            return err;
        }
    }

    ALOGE("vaylb-->Encoder stopping (req=%d)", gStopRequested);
    if (gVerbose) {
        ALOGE("vaylb-->Encoder stopping; recorded %u frames in %" PRId64 " seconds\n",
                debugNumFrames, nanoseconds_to_seconds(
                        systemTime(CLOCK_MONOTONIC) - startWhenNsec));
    }
    return NO_ERROR;
}

/*
 * Raw H.264 byte stream output requested.  Send the output to stdout
 * if desired.  If the output is a tty, reconfigure it to avoid the
 * CRLF line termination that we see with "adb shell" commands.
 */
FILE* ScreenRecord::prepareRawOutput(const char* fileName) {
    FILE* rawFp = NULL;

    if (strcmp(fileName, "-") == 0) {
        if (gVerbose) {
            ALOGE("vaylb-->ERROR: verbose output and '-' not compatible");
            return NULL;
        }
        rawFp = stdout;
    } else {
        rawFp = fopen(fileName, "w");
        if (rawFp == NULL) {
            ALOGE("vaylb-->fopen raw failed: %s\n", strerror(errno));
            return NULL;
        }
    }

    int fd = fileno(rawFp);
    if (isatty(fd)) {
        // best effort -- reconfigure tty for "raw"
        ALOGE("vaylb-->raw video output to tty (fd=%d)", fd);
        struct termios term;
        if (tcgetattr(fd, &term) == 0) {
            cfmakeraw(&term);
            if (tcsetattr(fd, TCSANOW, &term) == 0) {
                ALOGE("vaylb-->tty successfully configured for raw");
            }
        }
    }

    return rawFp;
}

/*
 * Main "do work" start point.
 *
 * Configures codec, muxer, and virtual display, then starts moving bits
 * around.
 */
status_t ScreenRecord::recordScreen(const char* fileName) {
    status_t err;

    // Start Binder thread pool.  MediaCodec needs to be able to receive
    // messages from mediaserver.
    sp<ProcessState> self = ProcessState::self();
    self->startThreadPool();

    // Get main display parameters.
    sp<IBinder> mainDpy = SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain);
    DisplayInfo mainDpyInfo;
    err = SurfaceComposerClient::getDisplayInfo(mainDpy, &mainDpyInfo);
    if (err != NO_ERROR) {
        ALOGE("vaylb-->ERROR: unable to get display characteristics\n");
        return err;
    }
    if (gVerbose) {
        ALOGE("vaylb-->Main display is %dx%d @%.2ffps (orientation=%u)\n",
                mainDpyInfo.w, mainDpyInfo.h, mainDpyInfo.fps,
                mainDpyInfo.orientation);
    }

    bool rotated = isDeviceRotated(mainDpyInfo.orientation);
    if (gVideoWidth == 0) {
        gVideoWidth = rotated ? mainDpyInfo.h : mainDpyInfo.w;
    }
    if (gVideoHeight == 0) {
        gVideoHeight = rotated ? mainDpyInfo.w : mainDpyInfo.h;
    }

    // Configure and start the encoder.
    sp<MediaCodec> encoder;
    sp<FrameOutput> frameOutput;
    sp<IGraphicBufferProducer> encoderInputSurface;
    if (gOutputFormat != FORMAT_FRAMES && gOutputFormat != FORMAT_RAW_FRAMES) {
        err = prepareEncoder(mainDpyInfo.fps, &encoder, &encoderInputSurface);

        if (err != NO_ERROR && !gSizeSpecified) {
            // fallback is defined for landscape; swap if we're in portrait
            bool needSwap = gVideoWidth < gVideoHeight;
            uint32_t newWidth = needSwap ? kFallbackHeight : kFallbackWidth;
            uint32_t newHeight = needSwap ? kFallbackWidth : kFallbackHeight;
            if (gVideoWidth != newWidth && gVideoHeight != newHeight) {
                ALOGE("Retrying with 720p");
                fprintf(stderr, "WARNING: failed at %dx%d, retrying at %dx%d\n",
                        gVideoWidth, gVideoHeight, newWidth, newHeight);
                gVideoWidth = newWidth;
                gVideoHeight = newHeight;
                err = prepareEncoder(mainDpyInfo.fps, &encoder,
                        &encoderInputSurface);
            }
        }
        if (err != NO_ERROR) return err;

        // From here on, we must explicitly release() the encoder before it goes
        // out of scope, or we will get an assertion failure from stagefright
        // later on in a different thread.
    } else {
        // We're not using an encoder at all.  The "encoder input surface" we hand to
        // SurfaceFlinger will just feed directly to us.
        frameOutput = new FrameOutput();
        err = frameOutput->createInputSurface(gVideoWidth, gVideoHeight, &encoderInputSurface);
        if (err != NO_ERROR) {
            return err;
        }
    }

    // Draw the "info" page by rendering a frame with GLES and sending
    // it directly to the encoder.
    // TODO: consider displaying this as a regular layer to avoid b/11697754
    if (gWantInfoScreen) {
        Overlay::drawInfoPage(encoderInputSurface);
    }

    // Configure optional overlay.
    sp<IGraphicBufferProducer> bufferProducer;
    sp<Overlay> overlay;
    if (gWantFrameTime) {
        // Send virtual display frames to an external texture.
        overlay = new Overlay();
        err = overlay->start(encoderInputSurface, &bufferProducer);
        if (err != NO_ERROR) {
            if (encoder != NULL) encoder->release();
            return err;
        }
        if (gVerbose) {
            printf("Bugreport overlay created\n");
        }
    } else {
        // Use the encoder's input surface as the virtual display surface.
        bufferProducer = encoderInputSurface;
    }

    // Configure virtual display.
    sp<IBinder> dpy;
    err = prepareVirtualDisplay(mainDpyInfo, bufferProducer, &dpy);
    if (err != NO_ERROR) {
        if (encoder != NULL) encoder->release();
        return err;
    }

    sp<MediaMuxer> muxer = NULL;
    FILE* rawFp = NULL;
    switch (gOutputFormat) {
        case FORMAT_MP4: {
            // Configure muxer.  We have to wait for the CSD blob from the encoder
            // before we can start it.
            muxer = new MediaMuxer(fileName, MediaMuxer::OUTPUT_FORMAT_MPEG_4);
            if (gRotate) {
                muxer->setOrientationHint(90);  // TODO: does this do anything?
            }
            break;
        }
        case FORMAT_H264:
        case FORMAT_FRAMES:
        case FORMAT_RAW_FRAMES: {
            rawFp = prepareRawOutput(fileName);
            if (rawFp == NULL) {
                if (encoder != NULL) encoder->release();
                return -1;
            }
            break;
        }
        default:
            ALOGE("vaylb-->ERROR: unknown format %d\n", gOutputFormat);
            abort();
    }

    if (gOutputFormat == FORMAT_FRAMES || gOutputFormat == FORMAT_RAW_FRAMES) {
        // TODO: if we want to make this a proper feature, we should output
        //       an outer header with version info.  Right now we never change
        //       the frame size or format, so we could conceivably just send
        //       the current frame header once and then follow it with an
        //       unbroken stream of data.

        // Make the EGL context current again.  This gets unhooked if we're
        // using "--bugreport" mode.
        // TODO: figure out if we can eliminate this
        frameOutput->prepareToCopy();
		
		int imageSize = gVideoWidth*gVideoHeight*4;
		uint8_t* imageAddr = (uint8_t*)malloc(imageSize);
		int loopcount = 0;
		orientation = mainDpyInfo.orientation;	  
        while (!gStopRequested) {
			#if 1
			//vaylb rotation check
            SurfaceComposerClient::getDisplayInfo(mainDpy,&mainDpyInfo);
            if (orientation != mainDpyInfo.orientation) {
            	orientation = mainDpyInfo.orientation;
                ALOGE("vaylb-->orientation changed, now %d", orientation);
				bool rotated = isDeviceRotated(orientation);
				if(frameOutput != NULL){
					frameOutput.clear();
					frameOutput = NULL;
					frameOutput = new FrameOutput();
				}
		        err = frameOutput->createInputSurface(rotated?gVideoHeight:gVideoWidth, rotated?gVideoWidth:gVideoHeight, &bufferProducer);
		        if (err != NO_ERROR) {
		            return err;
		        }

				err = prepareVirtualDisplay(mainDpyInfo, bufferProducer, &dpy);
			    if (err != NO_ERROR) {
			        return err;
			    }

				frameOutput->prepareToCopy();
				
            }
			#endif
			//TODO: compress RGB to jpg
			int compressSize = 0;
			//if(loopcount == 50){
			//	compressSize = frameOutput->captureFrame(imageAddr);
			//} else 
			compressSize = frameOutput->compressFrame(imageAddr,2000);//250000,2500

			if(compressSize == 110){
				//loopcount++;
				continue;
			}
			loopcount = 0;
			//ALOGE("vaylb-->get compressSize = %d",compressSize);

			int datasize = compressSize + sizeof(compressSize);
			while(!gStopRequested && mSendBuffer->getWriteSpace() < datasize) {
				ALOGE("vaylb-->waiting for DataBuffer can hold %d byte",datasize);
				signalDataTransmitor();
				sleep(1000000); //1ms
			}
			
			if(gStopRequested) {
				break;
			}
			
			mSendBuffer->Write((char*)&compressSize,sizeof(compressSize));
			mSendBuffer->Write((char*)imageAddr,compressSize);
			signalDataTransmitor();
           
        }
		free(imageAddr);
    } else {
        // Main encoder loop.
        err = runEncoder(encoder, muxer, rawFp, mainDpy, dpy,
                mainDpyInfo.orientation);
        if (err != NO_ERROR) {
            ALOGE("vaylb-->Encoder failed (err=%d)\n", err);
            // fall through to cleanup
        }

        if (gVerbose) {
            ALOGE("vaylb-->Stopping encoder and muxer\n");
        }
    }

    // Shut everything down, starting with the producer side.
    encoderInputSurface = NULL;
    SurfaceComposerClient::destroyDisplay(dpy);
    if (overlay != NULL) overlay->stop();
    if (encoder != NULL) encoder->stop();
    if (muxer != NULL) {
        // If we don't stop muxer explicitly, i.e. let the destructor run,
        // it may hang (b/11050628).
        muxer->stop();
    } else if (rawFp != stdout) {
        fclose(rawFp);
    }
    if (encoder != NULL) encoder->release();
	
	Vector<int>::iterator curr = mSlaveSockets.begin();
	Vector<int>::iterator end = mSlaveSockets.end();
	while(curr!=end){
		close(*curr);
		curr++;
	}
	mSlaveSockets.clear();
	close(mServerSocketFd);
	mServerSocketFd = NULL;
	ALOGE("vaylb-->record end.");

    return err;
}


/*
 * Parses a string of the form "1280x720".
 *
 * Returns true on success.
 */
bool ScreenRecord::parseWidthHeight(const char* widthHeight, uint32_t* pWidth,
        uint32_t* pHeight) {
    long width, height;
    char* end;

    // Must specify base 10, or "0x0" gets parsed differently.
    width = strtol(widthHeight, &end, 10);
    if (end == widthHeight || *end != 'x' || *(end+1) == '\0') {
        // invalid chars in width, or missing 'x', or missing height
        return false;
    }
    height = strtol(end + 1, &end, 10);
    if (*end != '\0') {
        // invalid chars in height
        return false;
    }

    *pWidth = width;
    *pHeight = height;
    return true;
}

/*
 * Accepts a string with a bare number ("4000000") or with a single-character
 * unit ("4m").
 *
 * Returns an error if parsing fails.
 */
status_t ScreenRecord::parseValueWithUnit(const char* str, uint32_t* pValue) {
    long value;
    char* endptr;

    value = strtol(str, &endptr, 10);
    if (*endptr == '\0') {
        // bare number
        *pValue = value;
        return NO_ERROR;
    } else if (toupper(*endptr) == 'M' && *(endptr+1) == '\0') {
        *pValue = value * 1000000;  // check for overflow?
        return NO_ERROR;
    } else {
        ALOGE("vaylb-->Unrecognized value: %s\n", str);
        return UNKNOWN_ERROR;
    }
}


void ScreenRecord::setSlaveNum(int num){
	if(mServerSocketFd != NULL) return;

	mServerSocketFd = socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(mServerSocketFd == -1){
		ALOGE("vaylb-->create socket error:%d",errno); //errno13 :    Permission denied
		return;
	}
	int on = 1; 
	setsockopt(mServerSocketFd, SOL_SOCKET, SO_KEEPALIVE, (void *)&on, sizeof(on));  
    setsockopt(mServerSocketFd, IPPROTO_TCP, TCP_NODELAY, (void *)&on, sizeof(on));  
	
	mServerAddress.sin_family = AF_INET;
	mServerAddress.sin_addr.s_addr = htonl(INADDR_ANY);
	mServerAddress.sin_port = htons(40004);

	mServerAddrLen = sizeof(mServerAddress);

	status_t res = bind(mServerSocketFd,(struct sockaddr*)&mServerAddress,mServerAddrLen);
	if(res == -1){
		ALOGE("vaylb-->bind socket fd = %d, error:%d",mServerSocketFd,errno);
		close(mServerSocketFd);
		return;
	}
	res = listen(mServerSocketFd,5);
	if(res == -1){
		ALOGE("vaylb-->listen socket error:%d",errno);
		close(mServerSocketFd);
		return;
	}

	do{
		struct sockaddr_in 			clientAddress;
		int addrLen = sizeof(clientAddress);
		ALOGE("---------------waiting for device connect------------------");
		int clientFd = accept(mServerSocketFd,(struct sockaddr*)&clientAddress,&addrLen);
		ALOGE("vaylb->new video connect,fd = %d, ip = %s",clientFd,inet_ntoa(clientAddress.sin_addr));
		mSlaveSockets.push_back(clientFd);
	}while(mSlaveSockets.size() < mSlaveNum);
	
	start_threads();
	recordScreen("/sdcard/screenrecord.264");
}

void ScreenRecord::stopRecord(){
	gStopRequested = true;
	stop_threads();
}

void ScreenRecord::signalDataTransmitor(){
	Mutex::Autolock _l(mTransmitLock);
	mDataTransmit.signal();
}

void ScreenRecord::sleep(long sleepNs){
	const struct timespec req = {0, sleepNs};
	nanosleep(&req, NULL);
}




//------------------------------------------------------------------------
//                                            VideoTransmitor
//------------------------------------------------------------------------
ScreenRecord::VideoTransmitor::VideoTransmitor(ScreenRecord * sr)
	:Thread(false /*canCallJava*/),
	mScreenRecord(sr)
{
	ALOGE("vaylb-->VideoTransmitor construct.");
}

ScreenRecord::VideoTransmitor::~VideoTransmitor()
{
	ALOGE("vaylb-->VideoTransmitor destruct.");
	mScreenRecord.clear();
	mScreenRecord = NULL;
}

bool ScreenRecord::VideoTransmitor::threadLoop()
{
	ALOGE("vaylb-->VideoTransmitor::threadLoop.");
	while (!exitPending())
    {
    	//long time2 = get_time_stamp();
    	Mutex::Autolock _l(mTransmitLock);
		mDataTransmit.wait(mTransmitLock);
		if(exitPending()) break;
		
		int datasize = 0;
		while(mScreenRecord->mSendBuffer->getReadSpace() > sizeof(datasize)){
			mScreenRecord->mSendBuffer->Read((char*)&datasize,sizeof(datasize));
			if(datasize>0){
				while(mScreenRecord->mSendBuffer->getReadSpace() < datasize){
					ALOGE("vaylb-->VideoTransmitor waiting for DataBuffer can read %d byte",datasize);
					sleep(1000000); //1ms
				}
				if(exitPending()) {
					ALOGE("vaylb-->VideoTransmitor:: threadLoop end.");
					return false;
				};
				mScreenRecord->mSendBuffer->Read((char*)mScreenRecord->mCompressAddr,datasize);
				bool flip = mScreenRecord->isDeviceRotated(mScreenRecord->orientation);
				int sendsize = compressRGBToJPEG((uint8_t*)mScreenRecord->mCompressAddr, (uint8_t*)mScreenRecord->mSendAddr, flip?gVideoHeight:gVideoWidth, flip?gVideoWidth:gVideoHeight);
				if(!sendData((unsigned char *)mScreenRecord->mSendAddr,sendsize)){
					ALOGE("vaylb-->VideoTransmitor send data error!");
					//mVideoShare->callbackfun(5);
				}
			}else{
				ALOGE("get frame size error");
			}
		}
    }
	
	Vector<int>::iterator curr = mSlaveSockets.begin();
	Vector<int>::iterator end = mSlaveSockets.end();
	while(curr!=end){
		close(*curr);
		curr++;
	}
	mSlaveSockets.clear();
	close(mServerSocketFd);
	mServerSocketFd = NULL;
	

	ALOGE("vaylb-->VideoTransmitor:: threadLoop end.");
    return false;
}

bool ScreenRecord::VideoTransmitor::sendData(unsigned char * addr,int size){
	//ALOGE("vaylb-->VideoTransmitor send data size = %d,total size = %d",size,size+sizeof(int));
	Vector<int>::iterator curr = mSlaveSockets.begin();
	Vector<int>::iterator end = mSlaveSockets.end();
	int send = htonl(size+sizeof(int));
	while(curr!=end){
		int res = write(*curr,(const void*)&send,sizeof(size));
		if(res == -1)
		{
			ALOGE("vaylb-->VideoShare write socket error %d, fd = %d",errno,mServerSocketFd);
			return false;
		}
		socket_write(*curr,addr,size);
		curr++;
	}
	return true;
}


 int ScreenRecord::VideoTransmitor::compressRGBToJPEG(uint8_t* inbuf, uint8_t* outbuf, int width, int height){
	struct jpeg_compress_struct cinfo;
	struct jpeg_error_mgr jerr;

	cinfo.err = jpeg_std_error( &jerr );
	jpeg_create_compress(&cinfo);
	//jpeg_stdio_dest(&cinfo, outfile);

	/* Setting the parameters of the output file here */
	cinfo.image_width = width;//width;
	cinfo.image_height = height;//width;
	cinfo.input_components = 3;
	cinfo.in_color_space = JCS_RGB;

	jpeg_set_defaults( &cinfo );
	size_t mem_size = width*height*3;
	jpeg_mem_dest(&cinfo, outbuf, mem_size);
	/* Now do the compression .. */
	jpeg_start_compress( &cinfo, TRUE );

	JSAMPROW buffer;
	//ALOGE("vaylb-->compressRGBAToJPEG width = %d, height = %d", width, height);
	
	while (cinfo.next_scanline < cinfo.image_height) {
        JSAMPLE *row = inbuf + 3 * cinfo.image_width * cinfo.next_scanline;
        jpeg_write_scanlines(&cinfo, &row, 1);
    }
	jpeg_finish_compress( &cinfo );
	int jpeg_image_size = jpeg_mem_size(&cinfo);
	jpeg_destroy_compress( &cinfo );
	return jpeg_image_size;

}

void ScreenRecord::VideoTransmitor::threadLoop_run(){
	run("SRVideoTransmitor", PRIORITY_URGENT_DISPLAY);
}

void ScreenRecord::VideoTransmitor::threadLoop_exit(){
	ALOGE("vaylb-->VideoTransmitor exit");
	this->requestExit();
	//this->requestExitAndWait();
}

void ScreenRecord::VideoTransmitor::sleep(long sleepNs){
	const struct timespec req = {0, sleepNs};
	nanosleep(&req, NULL);
}

}

